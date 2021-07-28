package GPULootSeedFinder.entities;

import jcuda.driver.CUdeviceptr;

public interface IStruct {
    void writeToDevice(CUdeviceptr pointer);
}
