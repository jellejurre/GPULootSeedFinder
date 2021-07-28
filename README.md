# GPULootSeedFinder

This is a program which generates loot seeds on the GPU. You can use this program to find all minecraft seeds with certain items in less than three days with a powerful enough graphics card. It practically just runs FeatureUtils code but on the GPU so it goes fast xd.

## Prerequisites

This code only works for minecraft 1.14+. If you really want something lower, tell us, we'll see if we can make it work on 1.13 or lower.

This code only runs on Nvidia GPUs. To run this code optimally you need to know the amount of CUDA cores and GPU Memory you have.

To run this code, you need to have the [Nvidia CUDA Toolkit](https://developer.nvidia.com/cuda-downloads) installed on your PC.

P.S. If you're running this on a loot table which has a book with an enchantWithLevelsFunction on it, it might be hella slow or not work at all. We don't know why and it's midnight and we're not dealing with CUDA's problems anymore. Feel free to open a PR if you know how to fix it :)

## Install Instructions

We wish it was as easy as telling you import something, but because it uses some cuda stuff dependent on your system, you're going to need to clone the repository and put the files in your own project, so the cuda folder in your resources folder and the GPULootSeedFinder folder in your java folder.

It sucks, but the speedup is worth it.

## Credit
Made by [jellejurre](https://github.com/jellejurre) (discord: @jellejurre#8585) and [dragontamerfred](https://github.com/KalleStruik) (discord: @dragontamerfred#2779)