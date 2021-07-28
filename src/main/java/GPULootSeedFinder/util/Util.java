package GPULootSeedFinder.util;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import kaptainwutax.featureutils.loot.item.Item;
import kaptainwutax.featureutils.loot.item.ItemStack;
import kaptainwutax.mcutils.util.data.Pair;

public class Util {
    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    public static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip();
        return buffer.getLong();
    }
    public static boolean checkRequirements(List<ItemStack> original, List<ItemStack> items){
        ArrayList<ItemStack> requirements = new ArrayList<>();
        original.forEach(x->requirements.add(new ItemStack(x.getItem(), x.getCount())));
        for (int j = 0; j < requirements.size(); j++) {
            for (int k = 0; k < items.size(); k++) {
                Item requirement = requirements.get(j).getItem();
                Item current = items.get(k).getItem();
                if (requirement.getName().equals(current.getName())) {
                    if (requirement.getEnchantments().size() > 0) {
                        int matchingEnchants = 0;
                        for (Pair<String, Integer> requirementEnchantment : requirement.getEnchantments()) {
                            for (Pair<String, Integer> itemEnchantment : current.getEnchantments()) {
                                if (itemEnchantment.getFirst().equals(requirementEnchantment.getFirst())){
                                    if (itemEnchantment.getSecond() >= requirementEnchantment.getSecond()) {
                                        matchingEnchants++;
                                    }
                                }
                            }
                        }
                        if (matchingEnchants==requirement.getEnchantments().size()) {
                            requirements.get(j).setCount(requirements.get(j).getCount() - items.get(k).getCount());
                        }
                    } else {
                        requirements.get(j).setCount(requirements.get(j).getCount() - items.get(k).getCount());
                    }
                }
            }
        }
        boolean currentCorrect = true;
        for (ItemStack requirement : requirements) {
            if(requirement.getCount()>0){
                currentCorrect = false;
            }
        }
        return currentCorrect;
    }
}
