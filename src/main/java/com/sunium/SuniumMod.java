package com.sunium;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.registries.IForgeRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Mod(modid = "sunium", name = "Sunium", version = "6.0",
        acceptedMinecraftVersions = "[1.12.2]")
public class SuniumMod {

    public static final String MODID = "sunium";
    private static final Logger LOG = LogManager.getLogger("Sunium");

    // ===================== ЦВЕТЫ =====================
    public static Block chamomile;
    public static Block lavender;
    public static Block ashbloom;
    public static Block bluebell;
    public static Block seaRose;
    public static Block whitePoppy;

    public static final List<Block> ALL_FLOWERS = new ArrayList<>();

    // ===================== КРЕАТИВ-ТАБ =====================
    public static final CreativeTabs SUNIUM_TAB = new CreativeTabs("sunium") {
        @Override
        public ItemStack getTabIconItem() {
            return new ItemStack(chamomile);
        }
    };

    // ===================== ИНИЦИАЛИЗАЦИЯ =====================
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("Sunium preInit");
        GameRegistry.registerTileEntity(TileEntityFlower.class, MODID + ":flower");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        GameRegistry.registerWorldGenerator(new FlowerGenerator(), 0);
        LOG.info("Sunium initialized");
    }

    // ===================== РЕГИСТРАЦИЯ =====================
    @Mod.EventBusSubscriber(modid = MODID)
    public static class RegistryHandler {

        @SubscribeEvent
        public static void registerBlocks(RegistryEvent.Register<Block> event) {
            IForgeRegistry<Block> r = event.getRegistry();

            chamomile = new FlowerBase("chamomile");
            lavender = new FlowerBase("lavender");
            ashbloom = new FlowerBase("ashbloom");
            bluebell = new FlowerBase("bluebell");
            seaRose = new FlowerBase("sea_rose");
            whitePoppy = new FlowerBase("white_poppy");

            ALL_FLOWERS.add(chamomile);
            ALL_FLOWERS.add(lavender);
            ALL_FLOWERS.add(ashbloom);
            ALL_FLOWERS.add(bluebell);
            ALL_FLOWERS.add(seaRose);
            ALL_FLOWERS.add(whitePoppy);

            r.registerAll(chamomile, lavender, ashbloom, bluebell, seaRose, whitePoppy);
        }

        @SubscribeEvent
        public static void registerItems(RegistryEvent.Register<Item> event) {
            IForgeRegistry<Item> r = event.getRegistry();
            r.register(new ItemBlockFlower(chamomile).setRegistryName("chamomile"));
            r.register(new ItemBlockFlower(lavender).setRegistryName("lavender"));
            r.register(new ItemBlockFlower(ashbloom).setRegistryName("ashbloom"));
            r.register(new ItemBlockFlower(bluebell).setRegistryName("bluebell"));
            r.register(new ItemBlockFlower(seaRose).setRegistryName("sea_rose"));
            r.register(new ItemBlockFlower(whitePoppy).setRegistryName("white_poppy"));
        }

        @SideOnly(Side.CLIENT)
        @SubscribeEvent
        public static void registerModels(ModelRegistryEvent event) {
            for (Block b : ALL_FLOWERS) {
                ModelLoader.setCustomModelResourceLocation(
                        Item.getItemFromBlock(b), 0,
                        new ModelResourceLocation(b.getRegistryName(), "inventory")
                );
            }
        }
    }

    // ===================== БЛОК ЦВЕТКА =====================
    public static class FlowerBase extends BlockFlower implements ITileEntityProvider {

        private static final AxisAlignedBB AABB = new AxisAlignedBB(0.3, 0, 0.3, 0.7, 0.6, 0.7);

        public FlowerBase(String name) {
            super(EnumFlowerType.POPPY);
            setRegistryName(name);
            setUnlocalizedName(MODID + "." + name);
            setCreativeTab(SUNIUM_TAB);
            setHardness(0f);
        }

        @Override
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
            return AABB;
        }

        @Override
        public boolean canPlaceBlockAt(World world, BlockPos pos) {
            Block b = world.getBlockState(pos.down()).getBlock();
            return b == Blocks.GRASS || b == Blocks.DIRT || b == Blocks.SAND
                    || b == Blocks.PODZOL || b == Blocks.MYCELIUM || b == Blocks.FARMLAND;
        }

        @Override
        public boolean canBlockStay(World world, BlockPos pos, IBlockState state) {
            return canPlaceBlockAt(world, pos);
        }

        @Override
        public net.minecraft.block.EnumBlockRenderType getRenderType(IBlockState state) {
            return net.minecraft.block.EnumBlockRenderType.MODEL;
        }

        @Override
        public boolean isOpaqueCube(IBlockState state) { return false; }

        @Override
        public boolean isFullCube(IBlockState state) { return false; }

        @Override
        public boolean hasTileEntity(IBlockState state) { return true; }

        @Nullable
        @Override
        public TileEntity createNewTileEntity(World world, int meta) {
            return new TileEntityFlower();
        }

        @Override
        public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                    net.minecraft.entity.EntityLivingBase placer, ItemStack stack) {
            super.onBlockPlacedBy(world, pos, state, placer, stack);

            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityFlower) {
                int mult = 1;
                if (stack.hasTagCompound() && stack.getTagCompound().hasKey("multiplier")) {
                    mult = stack.getTagCompound().getInteger("multiplier");
                    if (mult < 1) mult = 1;
                }
                ((TileEntityFlower) te).setMultiplier(mult);
            }
        }

        @Override
        public void breakBlock(World world, BlockPos pos, IBlockState state) {
            TileEntity te = world.getTileEntity(pos);
            int mult = 1;
            if (te instanceof TileEntityFlower) {
                mult = ((TileEntityFlower) te).getMultiplier();
            }

            if (!world.isRemote) {
                int dropCount = mult;
                int nextMult = mult * 2;

                for (int i = 0; i < dropCount; i++) {
                    ItemStack drop = new ItemStack(Item.getItemFromBlock(this), 1);

                    if (i == 0) {
                        NBTTagCompound tag = new NBTTagCompound();
                        tag.setInteger("multiplier", nextMult);
                        drop.setTagCompound(tag);
                    }

                    spawnAsEntity(world, pos, drop);
                }
            }

            super.breakBlock(world, pos, state);
            world.removeTileEntity(pos);
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return null;
        }

        @Override
        public int quantityDropped(Random random) {
            return 0;
        }
    }

    // ===================== ITEMBLOCK =====================
    public static class ItemBlockFlower extends ItemBlock {
        public ItemBlockFlower(Block block) {
            super(block);
        }

        @Override
        public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
            if (tab != SUNIUM_TAB) return;

            // обычный цветок
            items.add(new ItemStack(this, 1));

            // цветок с multiplier = 2 для теста
            ItemStack doubled = new ItemStack(this, 1);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("multiplier", 2);
            doubled.setTagCompound(tag);
            items.add(doubled);
        }
    }

    // ===================== TILEENTITY =====================
    public static class TileEntityFlower extends TileEntity {

        private int multiplier = 1;

        public int getMultiplier() { return multiplier; }

        public void setMultiplier(int m) {
            this.multiplier = m;
            markDirty();
        }

        @Override
        public void readFromNBT(NBTTagCompound compound) {
            super.readFromNBT(compound);
            multiplier = compound.getInteger("multiplier");
            if (multiplier < 1) multiplier = 1;
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound compound) {
            super.writeToNBT(compound);
            compound.setInteger("multiplier", multiplier);
            return compound;
        }

        @Override
        public NBTTagCompound getUpdateTag() {
            NBTTagCompound tag = super.getUpdateTag();
            tag.setInteger("multiplier", multiplier);
            return tag;
        }

        @Override
        public void handleUpdateTag(NBTTagCompound tag) {
            super.handleUpdateTag(tag);
            multiplier = tag.getInteger("multiplier");
        }
    }

    // ===================== ГЕНЕРАЦИЯ =====================
    public static class FlowerGenerator implements IWorldGenerator {
        @Override
        public void generate(Random random, int chunkX, int chunkZ, World world,
                             IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
            if (world.provider.getDimension() != 0) return;

            for (int i = 0; i < 4; i++) {
                int x = chunkX * 16 + random.nextInt(16);
                int z = chunkZ * 16 + random.nextInt(16);
                int y = world.getHeight(x, z);

                BlockPos pos = new BlockPos(x, y, z);
                Block below = world.getBlockState(pos.down()).getBlock();

                if (below == Blocks.GRASS && ALL_FLOWERS.size() > 0) {
                    Block flower = ALL_FLOWERS.get(random.nextInt(ALL_FLOWERS.size()));
                    world.setBlockState(pos, flower.getDefaultState());
                }
            }
        }
    }
}
