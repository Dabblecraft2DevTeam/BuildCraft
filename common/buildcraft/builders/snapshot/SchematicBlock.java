package buildcraft.builders.snapshot;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.data.LoadingException;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.Fluid;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SchematicBlock implements INBTSerializable<NBTTagCompound> {
    public int level;
    public Set<BlockPos> requiredBlockOffsets = new HashSet<>();
    public IBlockState blockState;
    public List<IProperty<?>> ignoredProperties = new ArrayList<>();
    public NBTTagCompound tileNbt;
    public List<String> ignoredTags = new ArrayList<>();
    public Rotation tileRotation = Rotation.NONE;
    public Block placeBlock;
    public Set<Block> canBeReplacedWithBlocks = new HashSet<>();
    public List<ItemStack> requiredItems = new ArrayList<>();
    public List<Fluid> requiredFluids = new ArrayList<>();

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("level", level);
        nbt.setTag(
                "requiredBlockOffsets",
                NBTUtilBC.writeCompoundList(
                        requiredBlockOffsets.stream()
                                .map(NBTUtil::createPosTag)
                )
        );
        nbt.setTag("blockState", NBTUtilBC.writeEntireBlockState(blockState));
        nbt.setTag(
                "ignoredProperties",
                NBTUtilBC.writeStringList(
                        ignoredProperties.stream()
                                .map(IProperty::getName)
                )
        );
        if (tileNbt != null) {
            nbt.setTag("tileNbt", tileNbt);
        }
        nbt.setTag("ignoredTags", NBTUtilBC.writeStringList(ignoredTags.stream()));
        nbt.setTag("tileRotation", NBTUtilBC.writeEnum(tileRotation));
        nbt.setString("placeBlock", Block.REGISTRY.getNameForObject(placeBlock).toString());
        nbt.setTag(
                "canBeReplacedWithBlocks",
                NBTUtilBC.writeStringList(
                        canBeReplacedWithBlocks.stream()
                                .map(Block.REGISTRY::getNameForObject)
                                .map(Object::toString)
                )
        );
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        level = nbt.getInteger("level");
        NBTUtilBC.readCompoundList(nbt.getTagList("requiredBlockOffsets", Constants.NBT.TAG_COMPOUND))
                .map(NBTUtil::getPosFromTag)
                .forEach(requiredBlockOffsets::add);
        try {
            blockState = NBTUtilBC.readEntireBlockState(nbt.getCompoundTag("blockState"));
        } catch (LoadingException e) {
            throw new RuntimeException(e);
        }
        NBTUtilBC.readStringList(nbt.getTagList("ignoredProperties", Constants.NBT.TAG_STRING))
                .map(propertyName ->
                        blockState.getPropertyKeys().stream()
                                .filter(property -> property.getName().equals(propertyName))
                                .findFirst()
                                .orElse(null)
                )
                .forEach(ignoredProperties::add);
        if (nbt.hasKey("tileNbt")) {
            tileNbt = nbt.getCompoundTag("tileNbt");
        }
        NBTUtilBC.readStringList(nbt.getTagList("ignoredTags", Constants.NBT.TAG_STRING)).forEach(ignoredTags::add);
        tileRotation = NBTUtilBC.readEnum(nbt.getTag("tileRotation"), Rotation.class);
        placeBlock = Block.REGISTRY.getObject(new ResourceLocation(nbt.getString("placeBlock")));
        NBTUtilBC.readStringList(nbt.getTagList("canBeReplacedWithBlocks", Constants.NBT.TAG_STRING))
                .map(ResourceLocation::new)
                .map(Block.REGISTRY::getObject)
                .forEach(canBeReplacedWithBlocks::add);
    }

    public SchematicBlock getRotated(Rotation rotation) {
        SchematicBlock schematicBlock = new SchematicBlock();
        schematicBlock.level = level;
        schematicBlock.requiredBlockOffsets = requiredBlockOffsets.stream()
                .map(blockPos -> blockPos.rotate(rotation))
                .collect(Collectors.toCollection(HashSet::new));
        schematicBlock.blockState = blockState.withRotation(rotation);
        schematicBlock.ignoredProperties = ignoredProperties;
        schematicBlock.tileNbt = tileNbt;
        schematicBlock.ignoredTags = ignoredTags;
        schematicBlock.tileRotation = tileRotation.add(rotation);
        schematicBlock.placeBlock = placeBlock;
        schematicBlock.canBeReplacedWithBlocks = canBeReplacedWithBlocks;
        schematicBlock.requiredItems = requiredItems;
        schematicBlock.requiredFluids = requiredFluids;
        return schematicBlock;
    }

    @SuppressWarnings("Duplicates")
    public boolean build(World world, BlockPos blockPos) {
        IBlockState newBlockState = blockState;
        if (placeBlock != blockState.getBlock()) {
            newBlockState = placeBlock.getDefaultState();
            for (IProperty<?> property : blockState.getPropertyKeys()) {
                if (newBlockState.getPropertyKeys().contains(property)) {
                    newBlockState = BlockUtil.copyProperty(
                            property,
                            newBlockState,
                            blockState
                    );
                }
            }
        }
        for (IProperty<?> property : ignoredProperties) {
            newBlockState = BlockUtil.copyProperty(
                    property,
                    newBlockState,
                    placeBlock.getDefaultState()
            );
        }
        if (world.setBlockState(blockPos, newBlockState, 11)) {
            Stream.of(
                    Stream.of(EnumFacing.values()).map(blockPos::offset),
                    requiredBlockOffsets.stream().map(blockPos::add),
                    Stream.of(blockPos)
            )
                    .flatMap(Function.identity())
                    .distinct()
                    .forEach(updatePos -> world.notifyNeighborsOfStateChange(updatePos, placeBlock, false));
            if (tileNbt != null && blockState.getBlock().hasTileEntity(blockState)) {
                NBTTagCompound newTileNbt = new NBTTagCompound();
                tileNbt.getKeySet().stream()
                        .map(key -> Pair.of(key, tileNbt.getTag(key)))
                        .forEach(kv -> newTileNbt.setTag(kv.getKey(), kv.getValue()));
                newTileNbt.setInteger("x", blockPos.getX());
                newTileNbt.setInteger("y", blockPos.getY());
                newTileNbt.setInteger("z", blockPos.getZ());
                ignoredTags.stream()
                        .filter(newTileNbt::hasKey)
                        .forEach(newTileNbt::removeTag);
                TileEntity tileEntity = TileEntity.create(world, newTileNbt);
                if (tileEntity != null) {
                    tileEntity.setWorld(world);
                    world.setTileEntity(blockPos, tileEntity);
                    if (tileRotation != Rotation.NONE) {
                        tileEntity.rotate(tileRotation);
                    }
                }
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("Duplicates")
    public boolean buildWithoutChecks(World world, BlockPos blockPos) {
        if (world.setBlockState(blockPos, blockState, 0)) {
            if (tileNbt != null && blockState.getBlock().hasTileEntity(blockState)) {
                NBTTagCompound newTileNbt = new NBTTagCompound();
                tileNbt.getKeySet().stream()
                        .map(key -> Pair.of(key, tileNbt.getTag(key)))
                        .forEach(kv -> newTileNbt.setTag(kv.getKey(), kv.getValue()));
                newTileNbt.setInteger("x", blockPos.getX());
                newTileNbt.setInteger("y", blockPos.getY());
                newTileNbt.setInteger("z", blockPos.getZ());
                TileEntity tileEntity = TileEntity.create(world, newTileNbt);
                if (tileEntity != null) {
                    tileEntity.setWorld(world);
                    world.setTileEntity(blockPos, tileEntity);
                    if (tileRotation != Rotation.NONE) {
                        tileEntity.rotate(tileRotation);
                    }
                }
                return true;
            }
        }
        return false;
    }
}
