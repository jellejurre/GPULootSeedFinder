package GPULootSeedFinder.entities;

import static jcuda.driver.JCudaDriver.cuMemAlloc;
import static jcuda.driver.JCudaDriver.cuMemcpyHtoD;

import jcuda.Sizeof;
import jcuda.Pointer;
import jcuda.driver.CUdeviceptr;

/*
typedef struct {
    long long length;
    int* availableEnchantments;
    int* availableEnchantmentLevels;
} AvailableEnchantmentResult;
 */

public class GPUAvailableEnchantmentResult implements IStruct {
    public static final int SIZE =
        Sizeof.LONG +
        2 * Sizeof.POINTER;
    public int[] availableEnchantments;
    public int[] availableEnchantmentLevels;

    public GPUAvailableEnchantmentResult() {}

    public GPUAvailableEnchantmentResult(int[] availableEnchantments,
                                         int[] availableEnchantmentLevels) {
        this.availableEnchantments = availableEnchantments;
        this.availableEnchantmentLevels = availableEnchantmentLevels;
    }

    public void writeToDevice(CUdeviceptr pointer) {
        if (availableEnchantments.length > 0) {
            // Copy the length of the arrays to the GPU.
            cuMemcpyHtoD(pointer, Pointer.to(new long[] {availableEnchantments.length}),
                Sizeof.LONG);

            // Allocate the availableEnchantments array on the GPU.
            CUdeviceptr availableEnchantmentsPtr = new CUdeviceptr();
            cuMemAlloc(availableEnchantmentsPtr, (long) availableEnchantments.length * Sizeof.INT);

            // Copy the availableEnchantments array to the GPU.
            cuMemcpyHtoD(availableEnchantmentsPtr, Pointer.to(availableEnchantments),
                (long) availableEnchantments.length * Sizeof.INT);

            // Allocate the availableEnchantmentLevels array on the GPU.
            CUdeviceptr availableEnchantmentLevelsPtr = new CUdeviceptr();
            cuMemAlloc(availableEnchantmentLevelsPtr,
                (long) availableEnchantmentLevels.length * Sizeof.INT);

            // Copy the availableEnchantmentLevels array to the GPU.
            cuMemcpyHtoD(availableEnchantmentLevelsPtr, Pointer.to(availableEnchantmentLevels),
                (long) availableEnchantmentLevels.length * Sizeof.INT);

            cuMemcpyHtoD(pointer.withByteOffset(Sizeof.LONG),
                Pointer.to(new Pointer[] {availableEnchantmentsPtr, availableEnchantmentLevelsPtr}),
                2L * Sizeof.POINTER);
        }
    }
}
