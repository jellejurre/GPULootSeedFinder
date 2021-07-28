package GPULootSeedFinder.entities;

import static jcuda.driver.JCudaDriver.cuMemAlloc;
import static jcuda.driver.JCudaDriver.cuMemcpyHtoD;


import jcuda.Pointer;
import jcuda.Sizeof;
import jcuda.driver.CUdeviceptr;

/*
typedef struct {
    int one;
    int two;
    int three;
    int four;
    int five;
    int six;
    int seven;
    int eight;
    int nine;
    int ten;
} EnchantmentArray;
 */

/*
typedef struct {
    int id;
    int count;
    int enchantmentCount;
    EnchantmentArray enchantmentIdsArray;
    EnchantmentArray enchantmentLevelsArray;
    int* enchantmentIds;
    int* enchantmentLevels;
} Item;
 */

public class GPUItem implements IStruct {
    public static final int SIZE =
        4 * Sizeof.INT +
//        2 * 10 * Sizeof.INT +
        2 * Sizeof.POINTER;

    public int id;
    public int count;
    public int[] enchantmentIdsArray = new int[0];
    public int[] enchantmentLevelsArray = new int[0];

    public GPUItem() {}

    public GPUItem(int id, int count, int[] enchantmentIdsArray, int[] enchantmentLevelsArray) {
        this.id = id;
        this.count = count;
        this.enchantmentIdsArray = enchantmentIdsArray;
        this.enchantmentLevelsArray = enchantmentLevelsArray;
    }

    @Override
    public void writeToDevice(CUdeviceptr pointer) {
        // Copy main info to GPU.
        cuMemcpyHtoD(pointer, Pointer.to(new int[] {id, count, enchantmentIdsArray.length, 0}), 4 * Sizeof.INT);

        if (enchantmentIdsArray.length > 0) {
            // Allocate space for arrays on GPU.
            CUdeviceptr enchantmentIdsArrayPtr = new CUdeviceptr();
            cuMemAlloc(enchantmentIdsArrayPtr, enchantmentIdsArray.length * Sizeof.INT);

            CUdeviceptr enchantmentLevelsArrayPtr = new CUdeviceptr();
            cuMemAlloc(enchantmentLevelsArrayPtr, enchantmentLevelsArray.length * Sizeof.INT);

            // Copy arrays to GPU.
            cuMemcpyHtoD(enchantmentIdsArrayPtr, Pointer.to(enchantmentIdsArray),
                (long) enchantmentIdsArray.length * Sizeof.INT);
            cuMemcpyHtoD(enchantmentLevelsArrayPtr, Pointer.to(enchantmentLevelsArray),
                (long) enchantmentLevelsArray.length * Sizeof.INT);

            // Store pointers to the arrays on the GPU.
            cuMemcpyHtoD(pointer.withByteOffset(4 * Sizeof.INT), Pointer.to(enchantmentIdsArrayPtr),
                Sizeof.POINTER);
            cuMemcpyHtoD(pointer.withByteOffset(4 * Sizeof.INT + Sizeof.POINTER),
                Pointer.to(enchantmentLevelsArrayPtr), Sizeof.POINTER);
        }

    }
}
