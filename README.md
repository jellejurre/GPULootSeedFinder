# GPULootSeedFinder

## Prerequisites

This code only works for minecraft 1.14+. If you really want something lower, tell us, we'll see if we can make it work on 1.13 or lower.

This code only runs on Nvidia GPUs. To run this code optimally you need to know the amount of CUDA cores and GPU Memory you have.

To run this code, you need to have the [Nvidia CUDA Toolkit](https://developer.nvidia.com/cuda-downloads) installed on your PC.

## Install Instructions

Add the following to your build.gradle repositories block
```    
    maven {url "https://jitpack.io"}
```
and the following to your build.gradle dependencies block:
```
    implementation('com.github.jellejurre:GPULootSeedFinder:1.0-SNAPSHOT'){transitive=false}
```

## Credit
Made by [jellejurre](https://github.com/jellejurre) (discord: @jellejurre#8585) and [dragontamerfred](https://github.com/KalleStruik) (discord: @dragontamerfred#2779)