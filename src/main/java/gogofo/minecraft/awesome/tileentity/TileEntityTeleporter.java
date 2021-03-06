package gogofo.minecraft.awesome.tileentity;

import gogofo.minecraft.awesome.TeleporterManager;
import gogofo.minecraft.awesome.block.BlockTeleportPortal;
import gogofo.minecraft.awesome.gui.GuiEnum;
import gogofo.minecraft.awesome.inventory.ContainerTeleporter;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;

public class TileEntityTeleporter extends AwesomeTileEntityMachine {
	private static final EnumFacing[] north_array = {
				EnumFacing.NORTH, 
				EnumFacing.UP, 
				EnumFacing.SOUTH, 
				EnumFacing.DOWN, 
				EnumFacing.NORTH
			};
	
	private static final EnumFacing[] east_array = {
				EnumFacing.EAST, 
				EnumFacing.UP, 
				EnumFacing.WEST, 
				EnumFacing.DOWN, 
				EnumFacing.EAST
			};
	
	private static final EnumFacing[] north_fill_array = {
			EnumFacing.NORTH, 
			EnumFacing.UP, 
			EnumFacing.SOUTH, 
		};

	private static final EnumFacing[] east_fill_array = {
			EnumFacing.EAST, 
			EnumFacing.UP, 
			EnumFacing.WEST, 
		};
	
	private final static int TARGET_TELEPORTER = 1;
	
	private Item key = null;
	private EnumFacing.Axis axis = null;
	
	public EnumFacing.Axis getAxis() {
		return axis;
	}
	
	@Override
	public String getName() {
		return "Teleporter";
	}

	@Override
	public int getInventoryStackLimit() {
		return 1;
	}
	
	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack) {
		return index == 0;
	}

	@Override
	public int getField(int id) {
		return 0;
	}

	@Override
	public void setField(int id, int value) {
	}

	@Override
	public int getFieldCount() {
		return 0;
	}

	@Override
	public void clear() {
		for (int i = 0; i < this.itemStackArray.length; ++i)
        {
            this.itemStackArray[i] = ItemStack.EMPTY;
        }
	}
	
	public String getGuiID()
    {
        return GuiEnum.TELEPORTER.guiName();
    }

	@Override
	public Container createContainer(InventoryPlayer playerInventory, EntityPlayer playerIn) {
		return new ContainerTeleporter(playerInventory, this);
	}
	
	@Override
	public void electricUpdate() {
		if (world.isRemote) {
			return;
		}
		
		if (itemStackArray[0].isEmpty() && key == null) {
			return;
		}
		
		if (itemStackArray[0].isEmpty()) {
			destroyPortal();
		} else if (itemStackArray[0].getItem() != key) {
			if (key != null) {
				TeleporterManager.instance.unregisterTeleporter(this);
				key = null;
			}
			
			if (findAndValidatePortalShape()) {
				key = itemStackArray[0].getItem();
				constructPortal();
			}
		} else if (key != null && !findAndValidatePortalShape()) {
			destroyPortal();
		}
	}
	
	@Override
	public void electricPowerlessUpdate() {
		if (world.isRemote) {
			return;
		}
		
		super.electricPowerlessUpdate();
		
		if (key != null) {
			destroyPortal();
		}
		
		if (key != null && !findAndValidatePortalShape()) {
			destroyPortal();
		}
	}
	
	private boolean findAndValidatePortalShape() {
		if (!validate2AirBlocksAboveTeleporter()) {
			return false;
		}
		
		if (axis == EnumFacing.Axis.Z) {
			return checkPortalShape(north_array, pos, 0, 0);
		} else if (axis == EnumFacing.Axis.X) {
			return checkPortalShape(east_array, pos, 0, 0);
		} else {
			if (checkPortalShape(north_array, pos, 0, 0)) {
				axis = EnumFacing.Axis.Z;
				return true;
			} else if (checkPortalShape(east_array, pos, 0, 0)) {
				axis = EnumFacing.Axis.X;
				return true;
			} else {
				return false;
			}
		}
	}
	
	private boolean checkPortalShape(EnumFacing[] directions, BlockPos pos, int curDir, int safty) {
		if (safty > 50) {
			return false;
		}
		
		if (world.isAirBlock(pos)) {
			return false;
		}
		
		if (pos.equals(this.pos) && curDir == directions.length-1) {
			return true;
		}
		
		if (curDir < directions.length-1) {
			BlockPos nextBlockPos = pos.offset(directions[curDir]);
			BlockPos turnBlockPos = pos.offset(directions[curDir+1]);
			
			if (!world.isAirBlock(turnBlockPos) && !isPortalBlock(turnBlockPos)) {
				return checkPortalShape(directions, turnBlockPos, curDir+1, safty+1);
			} else {
				return checkPortalShape(directions, nextBlockPos, curDir, safty+1);
			}
		} else {
			BlockPos nextBlockPos = pos.offset(directions[curDir]);
			return checkPortalShape(directions, nextBlockPos, curDir, safty+1);
		}
	}
	
	private boolean validate2AirBlocksAboveTeleporter() {
		BlockPos pos1 = pos.up();
		BlockPos pos2 = pos1.up();
		
		return (world.isAirBlock(pos1) || isPortalBlock(pos1)) && 
				(world.isAirBlock(pos2) || isPortalBlock(pos2));
	}
	
	private boolean isPortalBlock(BlockPos pos) {
		return world.getBlockState(pos).getBlock() instanceof BlockTeleportPortal;
	}
	
	private void constructPortal() {
		TeleporterManager.instance.registerTeleporter(this);
		
		if (axis == EnumFacing.Axis.Z) {
			recursivePortalConstruction(this.pos.up(), 
										north_fill_array);
		} else if (axis == EnumFacing.Axis.X) {
			recursivePortalConstruction(this.pos.up(), 
										east_fill_array);
		} else {
			// WTF?
		}
	}
	
	private void recursivePortalConstruction(BlockPos pos, EnumFacing[] directions) {
		if (!world.isAirBlock(pos)) {
			return;
		}
		
		createTeleportPortal(pos);
		
		for (EnumFacing direction : directions) {
			recursivePortalConstruction(pos.offset(direction), directions);
		}
	}
	
	private void createTeleportPortal(BlockPos pos) {
		world.setBlockState(pos,
				   gogofo.minecraft.awesome.init.Blocks.teleport_portal.getDefaultState().withProperty(BlockTeleportPortal.AXIS, axis));
		((TileEntityTeleportPortal)world.getTileEntity(pos)).setTeleporter(this.pos);
	}
	
	public void destroyPortal() {
		if (axis == EnumFacing.Axis.Z) {
			recursivePortalDestruction(this.pos.up(), 
										north_fill_array);
		} else if (axis == EnumFacing.Axis.X) {
			recursivePortalDestruction(this.pos.up(), 
										east_fill_array);
		} else {
			// WTF?
		}
		
		TeleporterManager.instance.unregisterTeleporter(this);
		key = null;
		axis = null;
	}
	
	private void recursivePortalDestruction(BlockPos pos, EnumFacing[] directions) {
		if (!isPortalBlock(pos)) {
			return;
		}
		
		world.setBlockToAir(pos);
		
		for (EnumFacing direction : directions) {
			recursivePortalDestruction(pos.offset(direction), directions);
		}
	}
	
	@Override
	public Integer[] getDefaultSlotForFace(EnumFacing face) {
		switch (face) {
		case UP:
			return new Integer[] {0};
		case DOWN:
			return new Integer[] {};
		case NORTH:
			return new Integer[] {};
		case SOUTH:
			return new Integer[] {};
		case EAST:
			return new Integer[] {};
		case WEST:
			return new Integer[] {};
		default:
			return new Integer[] {};
		}
	}

	@Override
	protected int getSlotCount() {
		return 1;
	}
	
	@Override
	public int getCustomSlotCount() {
		return 1;
	}

	@Override
	public int powerGeneratedWhenWorking() {
		return 0;
	}

	@Override
	public int powerRequiredWhenWorking() {
		return 3;
	}

	@Override
	public int powerGeneratedWhenIdle() {
		return 0;
	}

	@Override
	public int powerRequiredWhenIdle() {
		return 0;
	}

	@Override
	public boolean isWorking() {
		return findAndValidatePortalShape();
	}
	
	public Item getKey() {
		return key;
	}
}
