package GPULootSeedFinder;

import static GPULootSeedFinder.util.Util.checkRequirements;
import static jcuda.driver.JCudaDriver.cuCtxCreate;
import static jcuda.driver.JCudaDriver.cuDeviceGet;
import static jcuda.driver.JCudaDriver.cuInit;
import static jcuda.driver.JCudaDriver.cuLaunchKernel;
import static jcuda.driver.JCudaDriver.cuMemAlloc;
import static jcuda.driver.JCudaDriver.cuMemFree;
import static jcuda.driver.JCudaDriver.cuMemcpyDtoH;
import static jcuda.driver.JCudaDriver.cuMemcpyHtoD;
import static jcuda.driver.JCudaDriver.cuModuleGetFunction;
import static jcuda.driver.JCudaDriver.cuModuleLoad;
import static jcuda.runtime.JCuda.cudaDeviceSynchronize;


import GPULootSeedFinder.entities.GPUAvailableEnchantmentResult;
import GPULootSeedFinder.entities.GPUItem;
import GPULootSeedFinder.entities.GPUItemRoll;
import GPULootSeedFinder.entities.GPULootPool;
import GPULootSeedFinder.util.ReflectionUtils;
import GPULootSeedFinder.util.Util;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import jcuda.Pointer;
import jcuda.Sizeof;
import jcuda.driver.CUcontext;
import jcuda.driver.CUdevice;
import jcuda.driver.CUdeviceptr;
import jcuda.driver.CUfunction;
import jcuda.driver.CUmodule;
import jcuda.driver.JCudaDriver;
import kaptainwutax.featureutils.loot.LootContext;
import kaptainwutax.featureutils.loot.LootPool;
import kaptainwutax.featureutils.loot.LootTable;
import kaptainwutax.featureutils.loot.enchantment.Enchantment;
import kaptainwutax.featureutils.loot.enchantment.EnchantmentInstance;
import kaptainwutax.featureutils.loot.enchantment.Enchantments;
import kaptainwutax.featureutils.loot.entry.EmptyEntry;
import kaptainwutax.featureutils.loot.entry.ItemEntry;
import kaptainwutax.featureutils.loot.entry.LootEntry;
import kaptainwutax.featureutils.loot.function.ApplyDamageFunction;
import kaptainwutax.featureutils.loot.function.EffectFunction;
import kaptainwutax.featureutils.loot.function.EnchantRandomlyFunction;
import kaptainwutax.featureutils.loot.function.EnchantWithLevelsFunction;
import kaptainwutax.featureutils.loot.function.LootFunction;
import kaptainwutax.featureutils.loot.function.SetCountFunction;
import kaptainwutax.featureutils.loot.item.Item;
import kaptainwutax.featureutils.loot.item.ItemStack;
import kaptainwutax.featureutils.loot.roll.ConstantRoll;
import kaptainwutax.featureutils.loot.roll.LootRoll;
import kaptainwutax.featureutils.loot.roll.UniformRoll;
import kaptainwutax.mcutils.util.data.Pair;

public class GPULootSeedFinder {
    public static String pathToPtx;
    public static long maxMemory;
    public static long cudaCores;
    public static boolean setup = false;
    public static int printFirstDiagnosticsAfterXBatches = 3;
    public static int printDiagnosticsAfterXBatches = 100;

    private static long seedsPerCudaJob;
    private static long seedsInBatch;
    private static int outputLongsInBatch;
    private static int amountOfBatches;

    static {
        try {
            FileOutputStream fos = new FileOutputStream("./GPULootSeedFinder.ptx");
            fos.write(GPULootSeedFinder.class.getResourceAsStream("/cuda/GPULootSeedFinder.ptx")
                .readAllBytes());
            pathToPtx = "./GPULootSeedFinder.ptx";
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * Setup the program to use the right amount of memory and cuda cores
     * @param maxMemoryInMiB The amount of GPU memory that can be used (max 512 MiB)
     * @param cudaCores The amount of cuda cores that can be used at most.
     */
    public static void setup(double maxMemoryInMiB, long cudaCores) {
        GPULootSeedFinder.maxMemory = (long)(maxMemoryInMiB * 1024 * 1024 * 8);
        GPULootSeedFinder.cudaCores = cudaCores;

        long effectiveMaxMemory = Math.min(maxMemory, 512*1024*1024);
        seedsPerCudaJob = effectiveMaxMemory / cudaCores - ((effectiveMaxMemory / cudaCores) % 64);
        seedsInBatch = cudaCores * seedsPerCudaJob;
        outputLongsInBatch = (int) seedsInBatch / 64;
        amountOfBatches = (int) ((long) Math.pow(2, 48) / seedsInBatch);
        setup = true;
    }

    public static void crack(LootTable table, List<ItemStack> requirements) {
        crack(table, requirements, -1);
    }

    public static void crack(LootTable table, List<ItemStack> requirements, long startingSeed){
        crack(table, requirements, startingSeed, (long)Math.pow(2, 48));
    }

    public static void crack(LootTable table, List<ItemStack> requirements, long startingSeed, long endSeed){
        if (!setup) {
            throw new IllegalStateException("Cracker is not yet setup, please call the setup function to set it up.");
        }
        if (endSeed != -1) {
            amountOfBatches = (int) Math.ceil(endSeed / (double)seedsInBatch);
        }
        String outputString = getOutputString(requirements);

        if (startingSeed == -1) {
            startingSeed=0;
            try {
                Path path = Paths.get(outputString);
                if(Files.exists(path)) {
                    File inputFile = new File(outputString);
                    FileInputStream stream = new FileInputStream(inputFile);
                    stream.skipNBytes(inputFile.length() - 8);
                    startingSeed = Util.bytesToLong(stream.readNBytes(8)) + 1;
                    System.out.println("File found, starting at seed: " + startingSeed);
                }
            } catch (FileNotFoundException ignored) {
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        FileOutputStream writer;

        try {
            writer = new FileOutputStream(outputString, true);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // Enable exceptions and omit all subsequent error checks
        JCudaDriver.setExceptionsEnabled(true);

        // Initialize the driver and create a context for the first device.
        cuInit(0);
        CUdevice device = new CUdevice();
        cuDeviceGet(device, 0);
        CUcontext context = new CUcontext();
        cuCtxCreate(context, 0, device);

        // Load the ptx file.
        CUmodule module = new CUmodule();
        cuModuleLoad(module, pathToPtx);

        List<Item> itemArray = setupItemArray(table);

        requirementCheck(requirements, itemArray);

        List<String> enchantmentList = setupEnchantmentList();

        byte[][] enchantmentMatrix = generateEnchantmentMatrix(Enchantments.enchantmentRegistry, enchantmentList);

        //CUdeviceptr[] enchantmentPointerArray = new CUdeviceptr[enchantmentList.size()];
        CUdeviceptr enchantmentMatrixPointer = new CUdeviceptr();
        cuMemAlloc(enchantmentMatrixPointer, (long) enchantmentMatrix.length * Sizeof.POINTER);

        for (int i = 0; i < enchantmentMatrix.length; i++) {
            CUdeviceptr currentEnchantmentPointer = new CUdeviceptr();
            cuMemAlloc(currentEnchantmentPointer, enchantmentMatrix.length * Sizeof.BYTE);
            cuMemcpyHtoD(currentEnchantmentPointer, Pointer.to(enchantmentMatrix[i]), enchantmentMatrix.length * Sizeof.BYTE);

            cuMemcpyHtoD(enchantmentMatrixPointer.withByteOffset(i * Sizeof.POINTER), Pointer.to(currentEnchantmentPointer), Sizeof.POINTER);
        }


        CUdeviceptr lootPoolsPtr = new CUdeviceptr();
        cuMemAlloc(lootPoolsPtr, table.lootPools.length * (4 * Sizeof.INT +  Sizeof.POINTER));
        createLootPoolsOnGpu(lootPoolsPtr, table , itemArray, enchantmentList);

        CUdeviceptr requirementsArray = new CUdeviceptr();
        cuMemAlloc(requirementsArray, (long) requirements.size() * GPUItem.SIZE);

        createRequirementsOnGpu(requirementsArray, requirements, itemArray, enchantmentList);

        CUfunction function = new CUfunction();
        cuModuleGetFunction(function, module, "run");

        CUdeviceptr deviceOutput = new CUdeviceptr();
        cuMemAlloc(deviceOutput, outputLongsInBatch * Sizeof.LONG);

        int blockSizeX = 256;
        int gridSizeX = (int)Math.ceil((double) seedsInBatch / blockSizeX);
        long time = System.nanoTime();
        for (long batchNr = 0; batchNr < amountOfBatches; batchNr++) {

            // Set up the kernel parameters: A pointer to an array
            // of pointers which point to the actual values.
            Pointer kernelParameters = Pointer.to(
                Pointer.to(new long[]{seedsInBatch}),
                Pointer.to(new long[]{batchNr}),
                Pointer.to(new long[]{seedsPerCudaJob}),
                Pointer.to(new long[]{startingSeed}),
                Pointer.to(new int[]{table.lootPools.length}),
                Pointer.to(lootPoolsPtr),
                Pointer.to(new int[]{requirements.size()}),
                Pointer.to(requirementsArray),
                Pointer.to(new int[]{enchantmentMatrix.length}),
                Pointer.to(enchantmentMatrixPointer),
                Pointer.to(deviceOutput)
            );

            cuLaunchKernel(function,
                gridSizeX,  1, 1,      // Grid dimension
                blockSizeX, 1, 1,      // Block dimension
                0, null,               // Shared memory size and stream
                kernelParameters, null // Kernel- and extra parameters
            );

            cudaDeviceSynchronize();

            if ((batchNr % printDiagnosticsAfterXBatches == 0 && batchNr != 0) || batchNr == printFirstDiagnosticsAfterXBatches) {
                long currentSeed = (batchNr + 1) * seedsInBatch + startingSeed;
                float avgTimePerSeed = (System.nanoTime() - time) / (float) (currentSeed - startingSeed);

                System.out.println("================Diagnostics================");
                System.out.printf("Current batch: %s\n", batchNr);
                System.out.printf("Current seed: %s\n", currentSeed);
                System.out.printf("Percentage checked: %3.8f%%\n", ((currentSeed - startingSeed) / ((double) endSeed)) * 100);
                System.out.printf("AVG time per batch (in seconds): %3.2f\n", (System.nanoTime() - time) / (1000000000D * batchNr));
                System.out.printf("AVG time per seed (in nanoseconds): %3.2f\n", avgTimePerSeed);
                System.out.printf("Expected time till completion: %s\n", formatTime((long) (avgTimePerSeed * (endSeed - (currentSeed - startingSeed)))));
                System.out.println("===========================================");
            }

            long[] hostOutput = new long[outputLongsInBatch];
            cuMemcpyDtoH(Pointer.to(hostOutput), deviceOutput,
                outputLongsInBatch * Sizeof.LONG);
            try {
                for (int i = 0; i < hostOutput.length; i++) {
                    long section = hostOutput[i];
                    if (section == 0)
                        continue;

                    for (int j = 63; j >= 0; j--) {
                        boolean found = (section & 1) == 1;
                        if (found) {
                            long seed = batchNr * seedsInBatch + (64 * i) + j + startingSeed;
                            writer.write(Util.longToBytes(seed));
//                            System.out.println(batchNr * seedsInBatch + (64 * i) + j  + startingSeed);
                        }

                        section = section >> 1;
                    }
                }
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        cuMemFree(lootPoolsPtr);
        cuMemFree(requirementsArray);

        cuMemFree(deviceOutput);
    }

    private static void createLootPoolsOnGpu(CUdeviceptr lootPoolsPtr, LootTable table, List<Item> itemArray, List<String> enchantmentList) {
        GPUItemRoll noneItemRoll = new GPUItemRoll(0, 0, 0, 0, 0, 0, false, false, false, false, new int[0],
            new int[0], new GPUAvailableEnchantmentResult[0]);

        for (int k = 0; k < table.lootPools.length; k++) {
            LootPool lootPool = table.lootPools[k];
            long byteOffset = (long) k * GPULootPool.SIZE;

            GPULootPool gpuLootPool = new GPULootPool();

            gpuLootPool.optimizationArray = new GPUItemRoll[lootPool.precomputedWeights.length];

            if (lootPool.rolls instanceof ConstantRoll) {
                ConstantRoll constantRoll = (ConstantRoll) lootPool.rolls;
                gpuLootPool.min = constantRoll.value;
                gpuLootPool.max = constantRoll.value;
            } else {
                UniformRoll uniformRoll = (UniformRoll) lootPool.rolls;
                gpuLootPool.min = uniformRoll.min;
                gpuLootPool.max = uniformRoll.max;
            }

            for (int i = 0; i < lootPool.precomputedWeights.length; i++) {
                LootEntry entry = lootPool.precomputedWeights[i];

                if (entry instanceof EmptyEntry) {
                    gpuLootPool.optimizationArray[i] = noneItemRoll;
                } else {
                    GPUItemRoll gpuItemRoll = new GPUItemRoll();
                    gpuLootPool.optimizationArray[i] = gpuItemRoll;

                    ItemEntry itemEntry = ((ItemEntry) entry);

                    gpuItemRoll.id = itemArray.indexOf(itemEntry.item);

                    ArrayList<Integer> iApplicableEnchantments = new ArrayList<>();
                    ArrayList<Integer> iApplicableEnchantmentLevels = new ArrayList<>();

//                    GPUAvailableEnchantmentResult[] gpuAvailableEnchantmentResults = new GPUAvailableEnchantmentResult[0];
                    for (LootFunction function : itemEntry.lootFunctions) {
                        if (function instanceof SetCountFunction) {
                            SetCountFunction scFunction = ((SetCountFunction) function);
                            LootRoll iRoll = (LootRoll) ReflectionUtils
                                .getValueFromField(scFunction, "roll");
                            if (iRoll instanceof ConstantRoll) {
                                ConstantRoll constantRoll = ((ConstantRoll) iRoll);

                                gpuItemRoll.min = constantRoll.value;
                                gpuItemRoll.max = constantRoll.value;
                            } else {
                                UniformRoll uiRoll = (UniformRoll) iRoll;

                                gpuItemRoll.min = uiRoll.min;
                                gpuItemRoll.max = uiRoll.max;
                            }
                        } else if (function instanceof EnchantRandomlyFunction) {
                            gpuItemRoll.enchantRandomlyFunction = true;

                            EnchantRandomlyFunction erFunction = (EnchantRandomlyFunction) function;
                            List<Enchantment> applicableEnchantments =
                                (List<Enchantment>) ReflectionUtils
                                    .getValueFromField(erFunction, "applicableEnchantments");
                            for (Enchantment ench : applicableEnchantments) {
                                iApplicableEnchantments
                                    .add(enchantmentList.indexOf(ench.getName()));
                                iApplicableEnchantmentLevels.add(ench.getMaxLevel());
                            }
                        } else if (function instanceof EnchantWithLevelsFunction) {
                            gpuItemRoll.enchantWithLevelFunction = true;

                            EnchantWithLevelsFunction ewlFunction = (EnchantWithLevelsFunction) function;
                            ArrayList<EnchantmentInstance>[] availableEnchantmentResults =
                                (ArrayList<EnchantmentInstance>[]) ReflectionUtils
                                    .getValueFromField(ewlFunction, "availableEnchantmentResults");
                            gpuItemRoll.availableEnchantmentResults = new GPUAvailableEnchantmentResult[availableEnchantmentResults.length];
                            for (int j = 0; j < availableEnchantmentResults.length; j++) {
                                int maxSize = availableEnchantmentResults[j].stream()
                                    .mapToInt(x -> x.getRarity()).sum();
                                int[] currentEchantmentResults = new int[maxSize];
                                int[] currentEchantmentResultLevels = new int[maxSize];
                                int index = 0;
                                for (int l = 0; l < availableEnchantmentResults[j].size(); l++) {
                                    for (int m = 0;
                                         m < availableEnchantmentResults[j].get(l).getRarity();
                                         m++) {

                                        currentEchantmentResults[index] = enchantmentList.indexOf(
                                            availableEnchantmentResults[j].get(l).getName());
                                        currentEchantmentResultLevels[index] =
                                            availableEnchantmentResults[j].get(l).getLevel();
                                        index++;
                                    }
                                }
                                GPUAvailableEnchantmentResult result = new GPUAvailableEnchantmentResult(currentEchantmentResults, currentEchantmentResultLevels);
                                gpuItemRoll.availableEnchantmentResults[j] = result;
                            }
                            gpuItemRoll.minEnchantLevel = (int) ReflectionUtils.getValueFromField(ewlFunction, "minLevel");
                            gpuItemRoll.maxEnchantLevel = (int) ReflectionUtils.getValueFromField(ewlFunction, "maxLevel");
                        } else if (function instanceof ApplyDamageFunction) {
                            gpuItemRoll.applyDamageFunction = true;
                        } else if (function instanceof EffectFunction) {
                            gpuItemRoll.effectFunction = true;
                        }
                    }

                    gpuItemRoll.applicableEnchantments = new int[iApplicableEnchantments.size()];
                    gpuItemRoll.enchantmentLevels =
                        new int[iApplicableEnchantments.size()];
                    for (int j = 0; j < iApplicableEnchantments.size(); j++) {
                        gpuItemRoll.applicableEnchantments[j] = iApplicableEnchantments.get(j);
                        gpuItemRoll.enchantmentLevels[j] = iApplicableEnchantmentLevels.get(j);
                    }
                    HashMap<String, Integer> enchantabilityMap =
                        (HashMap<String, Integer>) ReflectionUtils
                            .getValueFromStaticField(EnchantWithLevelsFunction.class,
                                "enchantments");

                    gpuItemRoll.enchantability = enchantabilityMap.getOrDefault(itemEntry.item.getName(), 0);
                }
            }

            gpuLootPool.writeToDevice(lootPoolsPtr.withByteOffset(byteOffset));
        }
    }

    public static void estimateStats(LootTable table, List<ItemStack> requirements) {
        estimateStats(table, requirements, 20000000L);
    }

    public static void estimateStats(LootTable table, List<ItemStack> requirements, double seedCount) {
        double correct = 0;
        for (int i = 0; i < seedCount; i++) {
            List<ItemStack> output = table.generate(new LootContext(i));
            if (checkRequirements(requirements, output)) {
                correct++;
            }
        }
        if (correct == 0) {
            double fraction = 100 / seedCount;
            System.out.println("Size of check too low to find a seed.");
            System.out.println("If you want more accurate results, call this function with a higher seed count.");
            int decimals = (int)Math.ceil(Math.log10(seedCount)) - 2;
            String outputString = "Chance of rolling this are at most: %3." + decimals + "f%%.\n";
            System.out.printf(outputString, fraction);
            double GBs = Math.pow(2, 48) * fraction / 8 / 1024 / 1024 / 1024;
            System.out.printf("Amount of storage the entire seedspace would take at max: %5.5f GiB.\n", GBs);
            return;
        }
        double fraction = 100 * correct / seedCount;
        int decimals = (int)Math.ceil(Math.log10(seedCount)) - 2;
        String outputString = "Chance of rolling this are: %3." + decimals + "f%%.\n";
        System.out.printf(outputString, fraction);
        double GBs = Math.pow(2, 48) * fraction / 100 / 8 / 1024 / 1024 / 1024;
        System.out.printf("Amount of storage the entire seedspace would take: %5.5f GiB. \n", GBs);
    }

    private static String formatTime(long nanos) {
        long seconds = nanos / 1000000000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        seconds = seconds % 60;
        minutes = minutes % 60;
        hours = hours % 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(" days, ");
        }
        if (hours > 0) {
            sb.append(hours).append(" hours, ");
        }
        if (minutes > 0) {
            sb.append(minutes).append(" minutes, ");
        }

        sb.append(seconds).append(" seconds");

        return sb.toString();
    }

    private static void createRequirementsOnGpu(CUdeviceptr requirementsArray, List<ItemStack> requirements, List<Item> itemArray, List<String> enchantmentList){
        for (int i = 0; i < requirements.size(); i++) {
            ItemStack stack = requirements.get(i);
            int byteOffset = i * GPUItem.SIZE;
            GPUItem requirement = new GPUItem();

            requirement.id = itemArray.indexOf(new Item(stack.getItem().getName()));
            requirement.count = stack.getCount();

            int enchantmentCount = stack.getItem().getEnchantments().size();

            if (enchantmentCount > 0) {
                requirement.enchantmentIdsArray = new int[enchantmentCount];
                requirement.enchantmentLevelsArray = new int[enchantmentCount];

                for (int j = 0; j < stack.getItem().getEnchantments().size(); j++) {
                    Pair<String, Integer> enchantmentPair =
                        stack.getItem().getEnchantments().get(j);
                    requirement.enchantmentIdsArray[j] = enchantmentList.indexOf(enchantmentPair.getFirst());
                    requirement.enchantmentLevelsArray[j] = enchantmentPair.getSecond();
                }
            }

            requirement.writeToDevice(requirementsArray.withByteOffset(byteOffset));
        }
    }

    private static void requirementCheck(List<ItemStack> requirements, List<Item> itemArray) {
        for (ItemStack requirement : requirements) {
            boolean hasItem = false;
            for (Item item : itemArray) {
                if (item != null) {
                    if (item.getName().equals(requirement.getItem().getName())) {
                        hasItem=true;
                    }
                }
            }
            if (!hasItem) {
                throw new IllegalArgumentException("Requirement items are not in Loot Table: " + requirement.getItem().getName());
            }
        }
    }

    public static String getOutputString(List<ItemStack> requirements){
        String outputString = "";
        for (ItemStack requirement : requirements) {
            outputString += requirement.getCount();
            outputString += requirement.getItem().getName();
        }
        outputString+=".bin";
        return outputString;
    }

    private static List<Item> setupItemArray(LootTable table){
        List<Item> itemArray = new ArrayList<>();
        itemArray.add(null);
        for (LootPool lootPool : table.lootPools) {
            for(LootEntry entry : lootPool.lootEntries){
                if(entry instanceof ItemEntry){
                    Item item = ((ItemEntry)entry).item;
                    if(!itemArray.contains(item)){
                        itemArray.add(item);
                    }
                }
            }
        }
        return itemArray;
    }

    private static List<String> setupEnchantmentList(){
        List<String> enchantmentList = new ArrayList<>();
        for (String enchantment : Enchantments.SingleEnchants) {
            enchantmentList.add(enchantment);
        }

        for (Enchantment enchantment : Enchantments.enchantmentRegistry){
            if(!enchantmentList.contains(enchantment.getName())){
                enchantmentList.add(enchantment.getName());
            }
        }
        return enchantmentList;
    }

    private static byte[][] generateEnchantmentMatrix(List<Enchantment> registry, List<String> enchantmentList){
        byte[][] enchantmentMatrix = new byte[registry.size()][registry.size()];
        for (int i = 0; i < registry.size(); i++) {
            for (int j = 0; j < registry.size(); j++) {
                enchantmentMatrix[i][j] = -1;
            }
        }

        for (int i = 0; i < registry.size(); i++) {
            int enchantId1 = enchantmentList.indexOf(registry.get(i).getName());
            for (Enchantment enchantment : registry) {
                int enchantId2 = enchantmentList.indexOf(enchantment.getName());
                HashSet<String> incompatible = enchantment.getIncompatible();
                if (incompatible.contains(enchantmentList.get(enchantId1))) {
                    enchantmentMatrix[enchantId1][enchantId2] = 0;
                    enchantmentMatrix[enchantId2][enchantId1] = 0;
                } else {
                    if (enchantmentMatrix[enchantId1][enchantId2] == -1) {
                        enchantmentMatrix[enchantId1][enchantId2] = 1;
                    }
                }
            }
        }
        return enchantmentMatrix;
    }
}
