typedef struct {
    long long length;
    int* availableEnchantments;
    int* availableEnchantmentLevels;
} AvailableEnchantmentResult;

typedef struct {
    int id;
    int min;
    int max;
    int enchantability;
    int minEnchantLevel;
    int maxEnchantLevel;
    int padding;
    bool enchantRandomlyFunction;
    bool enchantWithLevelFunction;
    bool applyDamageFunction;
    bool effectFunction;
    int availableEnchantmentResultsLength;
    int applicableEnchantmentsLength;
    int* applicableEnchantments;
    int* enchantmentLevels;
    AvailableEnchantmentResult* availableEnchantmentResults;
} ItemRoll;

typedef struct {
    int id;
    int count;
    int enchantmentCount;
    int padding;
    int* enchantmentIds;
    int* enchantmentLevels;
} Item;

typedef struct {
    int min;
    int max;
    int optimizationArrayLength;
    int padding;
    ItemRoll* optimizationArray;
} LootPool;