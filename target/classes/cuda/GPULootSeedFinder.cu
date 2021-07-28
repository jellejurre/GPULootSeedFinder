extern "C++"

#include "GPUJRand.h"
#include "GPUStructs.h"
#include <math.h>

__device__ inline int filterEnchantments(bool* incompatible, AvailableEnchantmentResult* availableEnchantmentResultPtr, int enchantmentId, int enchantmentMatrixSize, bool** enchantmentMatrix) {
    AvailableEnchantmentResult availableEnchantmentResult = *availableEnchantmentResultPtr;
    int emptyCount = 0;
    for (char i = 0; i < availableEnchantmentResult.length; i++) {
        if (incompatible[i]) {
            emptyCount++;
        } else {
           if (!enchantmentMatrix[enchantmentId][availableEnchantmentResult.availableEnchantments[i]]) {
               incompatible[i] = true;
               emptyCount++;
           }
        }
    }
    return emptyCount;
}

extern "C" __global__ void run(long long amountInBatch, long long batchNr, int seedsPerCudaJob, long long startingSeed, int amountOfLootPools, LootPool lootPools[], int requirementsSize, Item requirements[], int enchantmentMatrixSize, bool** enchantmentMatrix, long long *output)
{

    int indexInBatch = blockIdx.x * blockDim.x + threadIdx.x;
    if (indexInBatch < amountInBatch / seedsPerCudaJob) {

        int64_t firstSeedInBatch = batchNr * amountInBatch;
        int longsPerCudaJob = seedsPerCudaJob / 64;
        int64_t firstLongForJob = (firstSeedInBatch / 64) + indexInBatch * longsPerCudaJob;

        for (int p = 0; p < longsPerCudaJob; p++) {
            int indexInOutput = indexInBatch * longsPerCudaJob + p;
            long long outputLong = 0;
            int64_t currentLong = (firstLongForJob + p);

            for (int indexInLong = 0; indexInLong < 64; indexInLong++) {
                int64_t seed = 0;
                int64_t *seedPtr = &seed;

                int64_t lootSeed = 64 * currentLong + indexInLong + startingSeed;

                setSeedFromInt(seedPtr, lootSeed);

                int requirementCounts[10];

                for (int i = 0; i < requirementsSize; i++) {
                    requirementCounts[i] = requirements[i].count;
                }

                for (int i = 0; i < amountOfLootPools; i++) {
                    LootPool lootPool = lootPools[i];

                    bool singlePool = true;
                    int firstItemId = lootPool.optimizationArray[0].id;
                    for (int item = 0; item < lootPool.optimizationArrayLength; item++){
                        if (lootPool.optimizationArray[item].id!=firstItemId) {
                            singlePool = false;
                        }
                    }
                    int rolls = 0;
                    int difference = (lootPool.max - lootPool.min + 1);
                    if (lootPool.max != lootPool.min) {
                        nextInt(seedPtr, difference, &rolls);
                    }

                    rolls = rolls + lootPool.min;
                    for (int roll = 0; roll < rolls; roll++) {
                        int index = 0;

                        ItemRoll itemRoll;
                        if (!singlePool) {
                            nextInt(seedPtr, lootPool.optimizationArrayLength, &index);
                            itemRoll = lootPool.optimizationArray[index];
                        } else {
                            itemRoll = lootPool.optimizationArray[0];
                        }

//                        printf("%i, %i, %i, %i, %i, %i, %i, %i, %i, %i\n", itemRoll.id, itemRoll.min, itemRoll.max, itemRoll.enchantability, itemRoll.minEnchantLevel, itemRoll.maxEnchantLevel, itemRoll.padding, itemRoll.enchantRandomlyFunction, itemRoll.applicableEnchantmentsLength);

                        // id 0 is empty roll.
                        if (itemRoll.id == 0) {
                            continue;
                        }

                        Item item;
                        item.id = itemRoll.id;
                        item.enchantmentCount = 0;

                        int enchantmentIds[10];
                        int enchantmentLevels[10];

                        item.enchantmentIds = enchantmentIds;
                        item.enchantmentLevels = enchantmentLevels;

                        if (itemRoll.max == itemRoll.min) {
                            item.count = itemRoll.max;
                        } else {
                            int number = 0;
                            nextInt(seedPtr, itemRoll.max - itemRoll.min + 1, &number);
                            item.count = itemRoll.min + number;
                        }

                        // Function ordering:
                        // ApplyDamageFunction -> EnchantRandomlyFunction

                        if (itemRoll.applyDamageFunction) {
                            advance(seedPtr);
                        }

                        if (itemRoll.enchantRandomlyFunction) {
                            int enchantmentIndex = 0;
//                            printf("ENCHANT RANDOMLY \n");

                            nextInt(seedPtr, itemRoll.applicableEnchantmentsLength, &enchantmentIndex);
                            int enchantmentId = itemRoll.applicableEnchantments[enchantmentIndex];
                            item.enchantmentIds[0] = enchantmentId;
                            if (enchantmentId > 9) {
                                int enchantmentLevel = 0;
                                nextInt(seedPtr, itemRoll.enchantmentLevels[enchantmentIndex], &enchantmentLevel);
                                item.enchantmentLevels[0] = enchantmentLevel + 1;
                            } else {
                                item.enchantmentLevels[0] = 1;
                            }
                            item.enchantmentCount++;

                        }

                        if (itemRoll.enchantWithLevelFunction) {
                            bool incompatible[152];

                            #pragma unroll
                            for (int o = 0; o < 152; o++) {
                                incompatible[o] = false;
                            }

                            int level = 0;
                            if (itemRoll.maxEnchantLevel == itemRoll.minEnchantLevel) {
                                level = itemRoll.minEnchantLevel;
                            } else {
                                nextInt(seedPtr, itemRoll.maxEnchantLevel - itemRoll.minEnchantLevel + 1, &level);
                                level += itemRoll.minEnchantLevel;
                            }

                            level += 1;

                            int randomCall1 = 0;
                            int randomCall2 = 0;

                            nextInt(seedPtr, itemRoll.enchantability / 4 + 1, &randomCall1);
                            nextInt(seedPtr, itemRoll.enchantability / 4 + 1, &randomCall2);

                            level += randomCall1 + randomCall2;

                            float amplifier = 0;

                            amplifier = (nextFloat(seedPtr) + nextFloat(seedPtr) - 1) * 0.15;

                            level = round(level + level * amplifier);

                            if (level < 1) {
                                level = 1;
                            }

                            AvailableEnchantmentResult availableEnchantments = itemRoll.availableEnchantmentResults[level];
                            if (availableEnchantments.length > 0) {
                                int index = 0;
                                nextInt(seedPtr, availableEnchantments.length, &index);

                                int enchantmentId = availableEnchantments.availableEnchantments[index];
                                int enchantmentLevel = availableEnchantments.availableEnchantmentLevels[index];

                                item.enchantmentIds[item.enchantmentCount] = enchantmentId;
                                item.enchantmentLevels[item.enchantmentCount] = enchantmentLevel;
                                item.enchantmentCount++;

                                int whileNextInt = 0;
                                nextInt(seedPtr, 50, &whileNextInt);


                                while (whileNextInt <= level) {
                                    int emptyCount = filterEnchantments(incompatible, &availableEnchantments, enchantmentId, enchantmentMatrixSize, enchantmentMatrix);
                                    if (emptyCount == availableEnchantments.length) {
                                        break;
                                    }

                                    index = 0;
                                    nextInt(seedPtr, availableEnchantments.length - emptyCount, &index);
                                    for (int j = 0; j < availableEnchantments.length; j++) {
                                        if(!incompatible[j]){
                                           index--;
                                        }

                                        if (index < 0) {
                                            enchantmentId = availableEnchantments.availableEnchantments[j];
                                            enchantmentLevel = availableEnchantments.availableEnchantmentLevels[j];
                                            break;
                                        }
                                    }

                                    item.enchantmentIds[item.enchantmentCount] = enchantmentId;
                                    item.enchantmentLevels[item.enchantmentCount] = enchantmentLevel;
                                    item.enchantmentCount++;

                                    level /= 2;
                                    nextInt(seedPtr, 50, &whileNextInt);
                                }
                            }
                        }

                        if (itemRoll.effectFunction) {
                            int effectId = 0;
                            nextInt(seedPtr, 6, &effectId);
                            advance(seedPtr);
                            //TODO: Store effect id with item.
                        }

//                        printf("%i, %i, %i \n", item.id, item.count, item.enchantmentCount);

                        for (int requirementIndex = 0; requirementIndex<requirementsSize; requirementIndex++){
                            Item requirement = requirements[requirementIndex];
                            if (item.id == requirement.id) {
                            if (requirement.enchantmentCount == item.enchantmentCount) {
                                    bool matches = true;
                                    for (int enchIdx = 0; enchIdx < requirement.enchantmentCount; enchIdx++) {
                                        bool found = false;
                                        for (int enchIdx2 = 0; enchIdx2 < item.enchantmentCount; enchIdx2++) {
                                            if (requirement.enchantmentIds[enchIdx] == item.enchantmentIds[enchIdx2]) {
                                                if (requirement.enchantmentLevels[enchIdx] <= item.enchantmentLevels[enchIdx2]) {
                                                    found = true;
                                                    break;
                                                }
                                            }
                                        }

                                        if (!found) {
                                            matches = false;
                                            break;
                                        }
                                    }

                                    if (matches) {
                                        requirementCounts[requirementIndex] -= item.count;
                                    }
                                }
                            }
                        }
                    }
                }

                bool failed = false;
                for (int requirementIndex = 0; requirementIndex<requirementsSize; requirementIndex++){
                    if (requirementCounts[requirementIndex] > 0) {
                        failed = true;
                    }
                }
                if (!failed) {
                   outputLong += 1;
                }

                if (indexInLong != 63) {
                    outputLong = outputLong << 1;
                }
            }
            output[indexInOutput] = outputLong;
        }
    }
}