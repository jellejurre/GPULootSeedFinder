package GPULootSeedFinder.entities;

/*
typedef struct {
    int id; // 0
    int min; // 4
    int max; // 8
    int enchantability; // 12
    int minEnchantLevel; // 16
    int maxEnchantLevel; // 20
    int padding; // 24
    bool enchantRandomlyFunction; // 28
    bool enchantWithLevelFunction; // 29
    bool applyDamageFunction; // 30
    bool effectFunction; // 31
    int availableEnchantmentResultsLength; // 32
    int applicableEnchantmentsLength; // 36
    int* applicableEnchantments; // 40
    int* enchantmentLevels; // 48
    AvailableEnchantmentResult* availableEnchantmentResults; // 56
} ItemRoll;
 */

import static jcuda.driver.JCudaDriver.cuMemAlloc;
import static jcuda.driver.JCudaDriver.cuMemcpyHtoD;


import java.util.Arrays;
import jcuda.driver.CUdeviceptr;
import jcuda.Sizeof;
import jcuda.Pointer;

public class GPUItemRoll implements IStruct {
    public static final int SIZE_MAIN_INFO = 7 * Sizeof.INT;
    public static final int SIZE_FUNCTION_FLAGS = 4 * Sizeof.BYTE;
    public static final int SIZE_ARRAY_LENGTHS = 2 * Sizeof.INT;
    public static final int SIZE_ARRAY_POINTERS = 3 * Sizeof.POINTER;
    public static final int SIZE =
        SIZE_MAIN_INFO +
        SIZE_FUNCTION_FLAGS +
        SIZE_ARRAY_LENGTHS +
        SIZE_ARRAY_POINTERS;

    public int id = 0;
    public int min = 1;
    public int max = 1;
    public int enchantability = 0;
    public int minEnchantLevel = 0;
    public int maxEnchantLevel = 0;
    public boolean enchantRandomlyFunction = false;
    public boolean enchantWithLevelFunction = false;
    public boolean applyDamageFunction = false;
    public boolean effectFunction = false;
    public int[] applicableEnchantments = new int[0];
    public int[] enchantmentLevels = new int[0];
    public GPUAvailableEnchantmentResult[] availableEnchantmentResults = new GPUAvailableEnchantmentResult[0];

    public GPUItemRoll() {}

    public GPUItemRoll(int id, int min, int max, int enchantability, int minEnchantLevel,
                       int maxEnchantLevel, boolean enchantRandomlyFunction,
                       boolean enchantWithLevelFunction, boolean applyDamageFunction,
                       boolean effectFunction, int[] applicableEnchantments, int[] enchantmentLevels,
                       GPUAvailableEnchantmentResult[] availableEnchantmentResults) {
        this.id = id;
        this.min = min;
        this.max = max;
        this.enchantability = enchantability;
        this.minEnchantLevel = minEnchantLevel;
        this.maxEnchantLevel = maxEnchantLevel;
        this.enchantRandomlyFunction = enchantRandomlyFunction;
        this.enchantWithLevelFunction = enchantWithLevelFunction;
        this.applyDamageFunction = applyDamageFunction;
        this.effectFunction = effectFunction;
        this.applicableEnchantments = applicableEnchantments;
        this.enchantmentLevels = enchantmentLevels;
        this.availableEnchantmentResults = availableEnchantmentResults;
    }

    public void writeToDevice(CUdeviceptr pointer) {
        // Copy the main info to the GPU.
        cuMemcpyHtoD(pointer, Pointer.to(new int[] {id, min, max, enchantability, minEnchantLevel, maxEnchantLevel, 0}), SIZE_MAIN_INFO);

        // Copy the function flags to the GPU.
        cuMemcpyHtoD(pointer.withByteOffset(SIZE_MAIN_INFO), Pointer.to(new byte[] {
            (byte) (enchantRandomlyFunction ? 1 : 0),
            (byte) (enchantWithLevelFunction ? 1 : 0),
            (byte) (applyDamageFunction ? 1 : 0),
            (byte) (effectFunction ? 1 : 0)
        }), SIZE_FUNCTION_FLAGS);

        // Copy the array lengths to the GPU.
        cuMemcpyHtoD(pointer.withByteOffset(SIZE_MAIN_INFO + SIZE_FUNCTION_FLAGS), Pointer.to(new int[] {availableEnchantmentResults.length, applicableEnchantments.length}), SIZE_ARRAY_LENGTHS);

        // Only allocate the applicableEnchantment arrays when they are used to save some memory.
        if (applicableEnchantments.length > 0) {
            // Create a pointer on the GPU for the applicableEnchantments array and allocate memory for it.
            CUdeviceptr applicableEnchantmentsPtr = new CUdeviceptr();
            cuMemAlloc(applicableEnchantmentsPtr, (long) applicableEnchantments.length * Sizeof.INT);

            // Copy the applicableEnchantments array to the GPU.
            cuMemcpyHtoD(applicableEnchantmentsPtr, Pointer.to(applicableEnchantments), (long) applicableEnchantments.length * Sizeof.INT);

//            for (int i = 0; i < applicableEnchantments.length; i++) {
//                System.out.printf("CPU: ItemID: %s, Enchantment %s: %s \n", id, i, applicableEnchantments[i]);
//            }

            // Create a pointer on the GPU for the enchantmentLevels array and allocate memory for it.
            CUdeviceptr enchantmentLevelsPtr = new CUdeviceptr();
            cuMemAlloc(enchantmentLevelsPtr, (long) enchantmentLevels.length * Sizeof.INT);

            // Copy the enchantmentLevels array to the GPU.
            cuMemcpyHtoD(enchantmentLevelsPtr, Pointer.to(enchantmentLevels), (long) enchantmentLevels.length * Sizeof.INT);

            // Copy the pointer to the arrays to the GPU.
            cuMemcpyHtoD(pointer.withByteOffset(SIZE_MAIN_INFO + SIZE_FUNCTION_FLAGS + SIZE_ARRAY_LENGTHS), Pointer.to(new Pointer[] {applicableEnchantmentsPtr, enchantmentLevelsPtr}), 2L * Sizeof.POINTER);
        }

        // Only allocate the availableEnchantmentResults arrays when they are used to save some memory.
        if (availableEnchantmentResults.length > 0) {
            // Create a pointer on the GPU for the applicableEnchantments array and allocate memory for it.
            CUdeviceptr availableEnchantmentResultsPtr = new CUdeviceptr();
            cuMemAlloc(availableEnchantmentResultsPtr, availableEnchantmentResults.length * GPUAvailableEnchantmentResult.SIZE);

            // Loop over all availableEnchantmentResults and copy them to the GPU within the array.
            for (int i = 0; i < availableEnchantmentResults.length; i++) {
                // Get the instance to write to the GPU.
                GPUAvailableEnchantmentResult availableEnchantmentResult = availableEnchantmentResults[i];
                // Copy the instance at the offset to the GPU.
                availableEnchantmentResult.writeToDevice(availableEnchantmentResultsPtr.withByteOffset(i * GPUAvailableEnchantmentResult.SIZE));
            }

            // Copy the pointer to the availableEnchantmentResults array to the GPU.
            cuMemcpyHtoD(pointer.withByteOffset(SIZE_MAIN_INFO + SIZE_FUNCTION_FLAGS + SIZE_ARRAY_LENGTHS + 2L * Sizeof.POINTER), Pointer.to(availableEnchantmentResultsPtr), Sizeof.POINTER);
        }
    }

    @Override
    public String toString() {
        return "GPUItemRoll{" +
            "id=" + id +
            ", min=" + min +
            ", max=" + max +
            ", enchantability=" + enchantability +
            ", minEnchantLevel=" + minEnchantLevel +
            ", maxEnchantLevel=" + maxEnchantLevel +
            ", enchantRandomlyFunction=" + enchantRandomlyFunction +
            ", enchantWithLevelFunction=" + enchantWithLevelFunction +
            ", applyDamageFunction=" + applyDamageFunction +
            ", effectFunction=" + effectFunction +
            ", applicableEnchantments=" + Arrays.toString(applicableEnchantments) +
            ", enchantmentLevels=" + Arrays.toString(enchantmentLevels) +
            ", availableEnchantmentResults=" + Arrays.toString(availableEnchantmentResults) +
            '}';
    }
}
