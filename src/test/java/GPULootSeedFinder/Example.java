package GPULootSeedFinder;

import static org.junit.jupiter.api.Assertions.assertTrue;


import GPULootSeedFinder.util.Reverser;
import GPULootSeedFinder.util.Util;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import jcuda.CudaException;
import kaptainwutax.featureutils.loot.LootContext;
import kaptainwutax.featureutils.loot.LootTable;
import kaptainwutax.featureutils.loot.MCLootTables;
import kaptainwutax.featureutils.loot.item.Item;
import kaptainwutax.featureutils.loot.item.ItemStack;
import kaptainwutax.featureutils.structure.RuinedPortal;
import kaptainwutax.mcutils.rand.ChunkRand;
import kaptainwutax.mcutils.state.Dimension;
import kaptainwutax.mcutils.util.data.Pair;
import kaptainwutax.mcutils.util.pos.CPos;
import kaptainwutax.mcutils.version.MCVersion;
import org.junit.jupiter.api.Test;

public class Example {

    @Test
    public void example() {
        //Setup the loot table we are going to roll at
        LootTable table = MCLootTables.RUINED_PORTAL_CHEST;

        //Apply the right minecraft version to that table. (our code only works for 1.14+)
        table.apply(MCVersion.v1_17);

        //Setup the requirements
        List<ItemStack> requirements = new ArrayList<>(Arrays.asList(
            new ItemStack(new Item("golden_sword", new ArrayList<>(Arrays.asList(new Pair<>("looting", 3))), new ArrayList<>()), 1),
            new ItemStack(new Item("golden_apple"), 2),
            new ItemStack(new Item("obsidian"), 8)
            ));


        //Setup the variables of the cracker. To find what these values should be for your GPU, look up your GPU memory size and your GPU cuda core count.
        //P.S. our code only runs on NVIDIA GPUs
        GPULootSeedFinder.setup(512, 5500);

        //Print some stats to know if it's smart to run this program.
        GPULootSeedFinder.estimateStats(table, requirements);

        //Call the cracker. This can take up to multiple days if your seed range is large enough, but it will print some stats while running.
        try {
            // This overwrites the path to the PTX file that contains the cuda code. This is not needed unless you are running tests.
            GPULootSeedFinder.pathToPtx = (Example.class.getResource("/").getPath() + "../classes/cuda/GPULootSeedFinder.ptx").substring(1);

            GPULootSeedFinder.crack(table, requirements, 0, 4269303063L);
        } catch(CudaException e) {
            System.out.println("Can't run this test because of GPU error. See below.");
            e.printStackTrace();
            return;
        }

        //Get the loot seeds from the newly created binary file.
        List<Long> lootSeeds = Reverser.getSeedsFromBinaryFile(GPULootSeedFinder.getOutputString(requirements));

        //Delete the binary because it's no longer needed. (You don't have to do this. It will see there is a binary file and continue where it left off.)
        Path file = Paths.get(GPULootSeedFinder.getOutputString(requirements));
        try {
            Files.delete(file);
        } catch (Exception e){
            e.printStackTrace();
        }

        //Create a FeatureUtils ruined portal to get the salt from.
        RuinedPortal portal = new RuinedPortal(Dimension.OVERWORLD, MCVersion.v1_17);

        //Turn the loot seeds into structure seeds and Chunk Pos'.
        HashMap<Long, CPos> structureSeeds = Reverser.getStructureSeedsFromLootSeeds(lootSeeds, 10, MCVersion.v1_17, portal.getDecorationSalt());

        //Select a structure seed-CPos combo to check.
        Long structureSeed = structureSeeds.keySet().iterator().next();

        CPos lootPos = structureSeeds.get(structureSeed);

        //Generate the loot from featureUtils with this loot seed to check.
        ChunkRand seedRand = new ChunkRand();
        seedRand.setDecoratorSeed(structureSeed, lootPos.toBlockPos().getX(), lootPos.toBlockPos().getZ(), portal.getDecorationSalt(), MCVersion.v1_17);
        LootContext context = new LootContext(seedRand.nextLong(), MCVersion.v1_17);
        List<ItemStack> chestContents = table.generate(context);

        //Check if the loot we made is the same as the featureUtils loot.
        assertTrue(Util.checkRequirements(requirements, chestContents));
    }

    //NOTE: THIS MIGHT BE SLOW OR NOT EVER EVEN FINISHED DUE TO SOMETHING WACKY GOING ON WITH BOOKS WITH ENCHANTWITHLEVELSFUNCTIONS ON SOME PCS
    @Test
    public void checkTables(){
        Class lootTables = MCLootTables.class;
        try {
            Field[] fields = lootTables.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                //Get a loot table.
                LootTable table = (LootTable) fields[i].get(null);
                if (table == MCLootTables.NULL)
                    continue;

                System.out.println("Starting to check table " + fields[i].getName());

                //Set the table to the right version.
                table.apply(MCVersion.v1_17);

                //Generate a requirement array.
                List<ItemStack> requirements = table.generate(new LootContext(1));

                //Setup the variables of the cracker. To find what these values should be for your GPU, look up your GPU memory size and your GPU cuda core count.
                //P.S. our code only runs on NVIDIA GPUs
                GPULootSeedFinder.setup(0.1, 5500);

                try {
                    // This overwrites the path to the PTX file that contains the cuda code. This is not needed unless you are running tests.
                    GPULootSeedFinder.pathToPtx = (Example.class.getResource("/").getPath() + "../classes/cuda/GPULootSeedFinder.ptx").substring(1);

                    //Call the cracker. This can take up to multiple days if your seed range is large enough, but it will print some stats while running.
                    GPULootSeedFinder.crack(table, requirements, 0, 1L);
                } catch(CudaException e) {
                    System.out.println("Can't run this test because of GPU error. See below.");
                    e.printStackTrace();
                    return;
                }

                //Get the loot seeds from the newly created binary file.
                List<Long> lootSeeds = Reverser.getSeedsFromBinaryFile(GPULootSeedFinder.getOutputString(requirements));

                //Delete the binary because it's no longer needed. (You don't have to do this if you wanna use the seeds in there.
                //GPULootSeedFinder will see there is a binary file and continue where it left off.)
                Path file = Paths.get(GPULootSeedFinder.getOutputString(requirements));
                try {
                    Files.delete(file);
                } catch (Exception e){
                    e.printStackTrace();
                }

                //Make sure that the list of seeds with the same loot as loot seed 1, has the loot seed 1.
                assertTrue(lootSeeds.contains(1L));

                System.out.println("Checked Table " + fields[i].getName());
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}