package GPULootSeedFinder.entities;

import static jcuda.driver.JCudaDriver.cuMemAlloc;
import static jcuda.driver.JCudaDriver.cuMemcpyHtoD;


import jcuda.Pointer;
import jcuda.Sizeof;
import jcuda.driver.CUdeviceptr;

//typedef struct {
//    int min;
//    int max;
//    int optimizationArrayLength;
//    int padding;
//    ItemRoll* optimizationArray;
//    } LootPool;

public class GPULootPool implements IStruct {
    public static final int SIZE_MAIN_INFO = 4 * Sizeof.INT;
    public static final int SIZE_ARRAY_POINTER = Sizeof.POINTER;
    public static final int SIZE =
        SIZE_MAIN_INFO + SIZE_ARRAY_POINTER;

    public int min;
    public int max;
    public GPUItemRoll[] optimizationArray;

    public GPULootPool() {

    }

    public GPULootPool(int min, int max, GPUItemRoll[] optimizationArray) {
        this.min = min;
        this.max = max;
        this.optimizationArray = optimizationArray;
    }

    @Override
    public void writeToDevice(CUdeviceptr pointer) {
        // Copy the main info to the GPU.
        cuMemcpyHtoD(pointer, Pointer.to(new int[] {min, max, optimizationArray.length, 0}), SIZE_MAIN_INFO);

        // Allocate space for the optimization array on the GPU.
        CUdeviceptr optimizationArrayPtr = new CUdeviceptr();
        cuMemAlloc(optimizationArrayPtr, (long) GPUItemRoll.SIZE * optimizationArray.length);

        // Copy all item rolls in the optimization array to the GPU.
        for (int i = 0; i < optimizationArray.length; i++) {
            long itemByteOffset = (long) i * GPUItemRoll.SIZE;
            optimizationArray[i].writeToDevice(optimizationArrayPtr.withByteOffset(itemByteOffset));
        }

        // Copy the optimization array pointer to the GPU.
        cuMemcpyHtoD(pointer.withByteOffset(SIZE_MAIN_INFO), Pointer.to(optimizationArrayPtr), SIZE_ARRAY_POINTER);
    }
}
