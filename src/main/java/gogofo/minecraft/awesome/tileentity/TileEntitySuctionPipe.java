package gogofo.minecraft.awesome.tileentity;

import gogofo.minecraft.awesome.block.BlockPipe;
import gogofo.minecraft.awesome.block.BlockSuctionPipe;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.ArrayList;

public class TileEntitySuctionPipe extends TileEntityPipe {
	public static final int SUCTION_COOLDOWN = 20;
	protected int suctionCooldown = -1;
	
	private static final BlockPipe refBlockPipe = new BlockSuctionPipe();
	
	@Override
	public void update() {
		super.update();
		
		if (world.isRemote) {
    		return;
    	}
		
		if (world.isBlockIndirectlyGettingPowered(pos) > 1) {
			return;
		}
		
		suctionCooldown -= 1;
		
		if (suctionCooldown <= 0) {
			doSuction();
			suctionCooldown = SUCTION_COOLDOWN;
		}
	}
	
	@Override
	protected BlockPipe getRefPipeBlock() {
		return refBlockPipe;
	}
	
	private void doSuction() {
		int recvSlot = getEmptySlotIndex();
		if (recvSlot < 0) {
			return;
		}
		
		BlockPipe tmpBlock = getRefPipeBlock();
		
		for (EnumFacing facing : EnumFacing.VALUES) {
			if (tryToSuckFromPos(tmpBlock, recvSlot, getPos().offset(facing), facing)) {
				markDirty();
				notifyUpdate(pos);
				notifyUpdate(getPos().offset(facing));
				break;
			}
		}
	}
	
	private boolean tryToSuckFromPos(BlockPipe tmpBlock, int recvSlot, BlockPos pos, EnumFacing facing) {
		if (!tmpBlock.canConnectTo(world, pos) || world.getBlockState(pos).getBlock() instanceof BlockPipe) {
			return false;
		}
		
		IInventory inventory = getInventoryAt(pos);
		if (inventory == null) {
			return false;
		}
		
		for (int i : getSlotIndexesForInventoryFacing(inventory, facing)) {
			ItemStack stack = inventory.getStackInSlot(i);
			if (!stack.isEmpty()) {
				setInventorySlotContents(recvSlot, createTransferredItem(stack, pos, true, stack.getCount()));
				inventory.removeStackFromSlot(i);
				return true;
			}
		}
		
		return false;
	}

	@Override
	protected ArrayList<BlockPos> getSecondaryDestsWithoutChecks(ItemStack stack, boolean attemptedPrimaryTransfer) {
		return new ArrayList<>();
	}
}
