#ifndef RANDOM_H
#define RANDOM_H

#include <cstdint>

#ifdef __CUDACC__
#define CUDA_CALLABLE_MEMBER __host__ __device__
#else
#define CUDA_CALLABLE_MEMBER
#endif


CUDA_CALLABLE_MEMBER inline int32_t Next(int64_t *seed, int bits)
{
    *seed = (*seed * 0x5deece66du + 0xBu) & ((1LLu << 48u) - 1);

    return (int32_t)(*seed >> (48u - bits));
}

CUDA_CALLABLE_MEMBER inline void advance(int64_t *seed)
{
    *seed = (*seed * 0x5deece66du + 0xBu) & ((1LLu << 48u) - 1);
}

CUDA_CALLABLE_MEMBER inline void setSeedFromInt(int64_t *seed, int64_t from) {
    *seed = (uint64_t)((uint64_t)from ^ 0x5deece66du) & ((1LLu << 48) - 1);
}

CUDA_CALLABLE_MEMBER inline void nextInt(int64_t *seed, int n, int *valPtr)
{
    //if (n <= 0) throw new ArgumentException("n must be positive");

//    printf("Random Size %i \n", n);

    if ((n & -n) == n) {  // i.e., n is a power of 2
        *valPtr = (int)((n * (int64_t)Next(seed, 31)) >> 31u);
        return;
    }

    long bits;
    do
    {
        bits = Next(seed, 31);
        *valPtr = bits % (int32_t)n;;
    }
    while (bits - *valPtr + (n - 1) < 0);
}

CUDA_CALLABLE_MEMBER inline bool nextBoolean(int64_t *seed) {
    return Next(seed, 1) != 0;
}

CUDA_CALLABLE_MEMBER inline double nextDouble(int64_t *seed)
{
    return (((int64_t)Next(seed, 26) << 27u) + Next(seed, 27))
      / (double)(1LLu << 53u);
}

CUDA_CALLABLE_MEMBER inline float nextFloat(int64_t *seed) {
    return Next(seed, 24) / ((float)(1 << 24));
}

CUDA_CALLABLE_MEMBER inline int64_t nextLong(int64_t *seed)
{
    return ((int64_t) Next(seed, 32) << 32) + Next(seed, 32);
}

#endif