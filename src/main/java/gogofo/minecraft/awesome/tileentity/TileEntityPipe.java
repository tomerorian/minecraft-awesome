package gogofo.minecraft.awesome.tileentity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import gogofo.minecraft.awesome.block.BlockPipe;
import gogofo.minecraft.awesome.block.BlockSuctionPipe;
import gogofo.minecraft.awesome.interfaces.IWrenchable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;

public class TileEntityPipe extends AwesomeTileEntityContainer implements ITickable, IWrenchable {
	public static final int TRANSFER_COOLDOWN_BASE = 2;
	public static final int TRANSFER_COOLDOWN_MIN = 20;

	public final static int IS_TRANSPARENT_IDX = 0;

	private static final BlockPipe refBlockPipe = new BlockPipe();
	public static final int PIPE_SLOT_COUNT = 27;

	private boolean isTransparent = false;

	@Override
	public void update() {
		if (world.isRemote) {
    		return;
    	}
		
		for (int i = 0; i < getTransferSlotCount(); i++) {
			ItemStack stack = super.getStackInSlot(i);
			if (!stack.isEmpty()) {
				decStackCooldown(stack);
				
				if (getStackCooldown(stack) <= 0) {
					transferStack(stack, i);
				}
			}
		}
	}
	
	private void transferStack(ItemStack stack, int index) {
		BlockPipe refBlock = getRefPipeBlock();
		
		EnumFacing[] randomFacing = EnumFacing.VALUES.clone();
		Collections.shuffle(Arrays.asList(randomFacing));
		
		ArrayList<EnumFacing> checkOrder = new ArrayList<EnumFacing>();
		
		int nonPipeIndex = 0;
		for (EnumFacing facing : randomFacing) {
			BlockPos dest = getPos().offset(facing);
			
			if (world.getBlockState(dest).getBlock() instanceof BlockPipe) {
				checkOrder.add(facing);
			} else {
				checkOrder.add(nonPipeIndex, facing);
				nonPipeIndex += 1;
			}
		}

		boolean attemptedPrimaryTransfer = false;

		for (EnumFacing facing : checkOrder) {
			BlockPos dest = getPos().offset(facing);
			
			if (!canTransferTo(stack, facing)) {
				continue;
			}

			attemptedPrimaryTransfer = true;
			
			if (tryToTransfer(refBlock, stack, index, dest)) {
				return;
			}
		}
		
		for (BlockPos secondaryDest : getSecondaryDestsWithoutChecks(stack, attemptedPrimaryTransfer)) {
			if (tryToTransfer(refBlock, stack, index, secondaryDest)) {
				return;
			}
		}
	}
	
	protected boolean canTransferTo(ItemStack stack, EnumFacing facing) {
		return canTransferTo(stack, facing, false);
	}
	
	protected boolean canTransferTo(ItemStack stack, EnumFacing facing, boolean allowOrigin) {
		BlockPos origin = getStackOrigin(stack);
		BlockPos dest = getPos().offset(facing);
		
		if (world.getBlockState(dest).getBlock() instanceof BlockSuctionPipe) {
			return false;
		}
		
		if (!allowOrigin && origin.equals(dest)) {
			return false;
		}
		
		return true;
	}
	
	protected ArrayList<BlockPos> getSecondaryDestsWithoutChecks(ItemStack stack, boolean attemptedPrimaryTransfer) {
		ArrayList<BlockPos> dests = new ArrayList<>();
		
		BlockPos origin = getStackOrigin(stack);
		
		if (!(world.getBlockState(origin).getBlock() instanceof BlockSuctionPipe)) {
			dests.add(getStackOrigin(stack));
		}
		
		return dests;
	}
	
	protected int[] getSlotIndexesForInventoryFacing(IInventory inventory, EnumFacing facing) {
		int slots[];
		
		if (inventory instanceof ISidedInventory) {
			ISidedInventory sidedInventory = (ISidedInventory)inventory;
			slots = sidedInventory.getSlotsForFace(facing.getOpposite());
		} else {
			slots = new int[inventory.getSizeInventory()];
			for (int i = 0; i < inventory.getSizeInventory(); i++) {
				slots[i] = i;
			}
		}
		
		return slots;
	}
	
	protected EnumFacing facingForCloseBlock(BlockPos pos) {
		if (getPos().up().equals(pos)) {
			return EnumFacing.UP;
		} else if (getPos().down().equals(pos)) {
			return EnumFacing.DOWN;
		} else if (getPos().north().equals(pos)) {
			return EnumFacing.NORTH;
		} else if (getPos().south().equals(pos)) {
			return EnumFacing.SOUTH;
		} else if (getPos().east().equals(pos)) {
			return EnumFacing.EAST;
		} else if (getPos().west().equals(pos)) {
			return EnumFacing.WEST;
		} else {
			return null;
		}
	}
	
	private boolean tryToTransfer(BlockPipe refPipeBlock, ItemStack sentStack, int sentSlot, BlockPos pos) {
		EnumFacing facing = facingForCloseBlock(pos);
		if (facing == null) {
			return false;
		}
		
		if (!refPipeBlock.canConnectTo(world, pos)) {
			return false;
		}
		
		IInventory inventory = getInventoryAt(pos);
		if (inventory == null) {
			return false;
		}
		
		for (int i : getSlotIndexesForInventoryFacing(inventory, facing)) {
			if (!inventory.isItemValidForSlot(i, sentStack)) {
				continue;
			}

			ItemStack stack = inventory.getStackInSlot(i);
			if (stack.isEmpty()) {
				inventory.setInventorySlotContents(i,
												   createTransferredItem(sentStack,
																		 getPos(),
																		 world.getBlockState(pos).getBlock() instanceof BlockPipe,
																		 sentStack.getCount()));
				decrStackSize(sentSlot, sentStack.getCount());
				markDirty();
				notifyUpdate(getPos());
				notifyUpdate(pos);
				
				return true;
			} else if (stack.getItem() == sentStack.getItem() && 
					   stack.isItemEqual(sentStack) &&
					   stack.getMetadata() == sentStack.getMetadata() &&
					   stack.getCount() < inventory.getInventoryStackLimit() &&
					   stack.getCount() < stack.getMaxStackSize() &&
					   compareEntityTag(stack, sentStack)) {
				int available = Math.min(inventory.getInventoryStackLimit(), stack.getMaxStackSize()) - stack.getCount();
				int transfer = Math.min(sentStack.getCount(), available);
				stack.grow(transfer);
				inventory.setInventorySlotContents(i, stack);
				decrStackSize(sentSlot, transfer);
				markDirty();
				notifyUpdate(getPos());
				notifyUpdate(pos);

				return true;
			}
		}
		
		return false;
	}

	protected boolean compareEntityTag(ItemStack stack1, ItemStack stack2) {
		if (stack1.getTagCompound() == null || !stack1.getTagCompound().hasKey("EntityTag")) {
			return true;
		}

		if (stack2.getTagCompound() == null || !stack2.getTagCompound().hasKey("EntityTag")) {
			return false;
		}

		return stack1.getTagCompound().getTag("EntityTag").equals(stack2.getTagCompound().getTag("EntityTag"));
	}

	private void decStackCooldown(ItemStack stack) {
		int cooldown = getStackCooldown(stack) - 1;
		
		setStackCooldown(stack, cooldown);
	}
	
	private NBTTagCompound getTagCompound(ItemStack stack) {
		if (stack.getTagCompound() == null) {
			stack.setTagCompound(new NBTTagCompound());
		}
		
		if (stack.getTagCompound().getTag("pipe") == null) {
			stack.getTagCompound().setTag("pipe", new NBTTagCompound());
		}
		
		return ((NBTTagCompound)stack.getTagCompound().getTag("pipe"));
	}
	
	private void clearTagCompound(ItemStack stack) {
		if (stack.getTagCompound() == null) {
			return;
		}
		
		stack.getTagCompound().removeTag("pipe");

		if (stack.getTagCompound().getKeySet().isEmpty()) {
			stack.setTagCompound(null);
		}
	}

	public void clearAllStackTags() {
		for (int i = 0; i < getTransferSlotCount(); i++) {
			ItemStack stack = super.getStackInSlot(i);
			clearTagCompound(stack);
		}
	}
	
	public int getStackCooldown(ItemStack stack) {
		return getTagCompound(stack).getInteger("transferCooldown");
	}
	
	private void setStackCooldown(ItemStack stack, int cooldown) {
		getTagCompound(stack).setInteger("transferCooldown", cooldown);
	}
	
	private BlockPos getStackOrigin(ItemStack stack) {
		return new BlockPos(getTagCompound(stack).getInteger("origin-x"),
							getTagCompound(stack).getInteger("origin-y"),
							getTagCompound(stack).getInteger("origin-z"));
	}
	
	private void setStackOrigin(ItemStack stack, BlockPos origin) {
		getTagCompound(stack).setInteger("origin-x", origin.getX());
		getTagCompound(stack).setInteger("origin-y", origin.getY());
		getTagCompound(stack).setInteger("origin-z", origin.getZ());
	}
	
	private void setOiginalTagCompoundIfNotSet(ItemStack stack, ItemStack original) {
		if (!getTagCompound(stack).getBoolean("has-original-tag")) {
			getTagCompound(stack).setBoolean("has-original-tag", true);
			
			NBTTagCompound tag = original.getTagCompound();
			if (tag != null) {
				getTagCompound(stack).setTag("original-tag", tag);
			}
		} 
	}
	
	private NBTTagCompound getOiginalTagCompound(ItemStack stack) {
		return (NBTTagCompound)getTagCompound(stack).getTag("original-tag");
	}
	
	protected int getEmptySlotIndex() {
		for (int i = 0; i < getTransferSlotCount(); i++) {
			if (super.getStackInSlot(i).isEmpty()) {
				return i;
			}
		}
		
		return -1;
	}

	public int getTransferSlotCount() {
		return PIPE_SLOT_COUNT;
	}
	
	protected IInventory getInventoryAt(BlockPos pos) {
		TileEntity te = world.getTileEntity(pos);
		
		if (!(te instanceof IInventory)) {
			return null;
		}
		
		return (IInventory)te;
	}
	
	protected ItemStack createTransferredItem(ItemStack original, BlockPos origin, boolean isForPipeBlock, int size) {
		ItemStack transferredItem = new ItemStack(original.getItem(), size, original.getMetadata());
		 if (original.getTagCompound() != null) {
            transferredItem.setTagCompound(original.getTagCompound().copy());
        }
		 
		setOiginalTagCompoundIfNotSet(transferredItem, original);
		 
		if (isForPipeBlock) {
			setStackCooldown(transferredItem, Math.max(TRANSFER_COOLDOWN_MIN, TRANSFER_COOLDOWN_BASE * transferredItem.getCount()));
			setStackOrigin(transferredItem, origin);
		} else {
			NBTTagCompound tag = getOiginalTagCompound(transferredItem);
			if (tag != null) {
				transferredItem.setTagCompound(tag.copy());
			} else {
				transferredItem.setTagCompound(null);
			}
		}
		
		return transferredItem;
	}
	
	protected BlockPipe getRefPipeBlock() {
		return refBlockPipe;
	}

	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack) {
		return true;
	}

	@Override
	public int getField(int id) {
		switch (id) {
			case IS_TRANSPARENT_IDX:
				return isTransparent ? 1 : 0;
		}

		return 0;
	}

	@Override
	public void setField(int id, int value) {
		switch (id) {
			case IS_TRANSPARENT_IDX:
				isTransparent = value == 1;
		}
	}

	@Override
	public int getFieldCount() {
		return 1;
	}

	@Override
	public String getName() {
		return "Pipe";
	}

	@Override
	public Container createContainer(InventoryPlayer playerInventory, EntityPlayer playerIn) {
		return new ContainerChest(playerInventory, this, playerIn);
	}

	@Override
	public String getGuiID() {
		return "minecraft:chest";
	}

	protected Integer[] getSlotsGeneric(int count) {
		Integer [] slots = new Integer[count];
		for (int i = 0; i < count; i++) {
			slots[i] = i;
		}
		return slots;
	}
	
	@Override
	public Integer[] getDefaultSlotForFace(EnumFacing face) {
		switch (face) {
		case UP:
			return getSlotsGeneric(PIPE_SLOT_COUNT);
		case DOWN:
			return getSlotsGeneric(PIPE_SLOT_COUNT);
		case NORTH:
			return getSlotsGeneric(PIPE_SLOT_COUNT);
		case SOUTH:
			return getSlotsGeneric(PIPE_SLOT_COUNT);
		case EAST:
			return getSlotsGeneric(PIPE_SLOT_COUNT);
		case WEST:
			return getSlotsGeneric(PIPE_SLOT_COUNT);
		default:
			return new Integer[] {};
		}
	}

	@Override
	protected int getSlotCount() {
		return PIPE_SLOT_COUNT;
	}

	@Override
	protected int getUpgradeCount() {
		return 0;
	}

	@Override
	public int getCustomSlotCount() {
		return 0;
	}
	
	@Override
    public int getInventoryStackLimit()
    {
        return 64;
    }
	
	@Override
    public ItemStack getStackInSlot(int index)
    {
		if (itemStackArray[index].isEmpty()) {
			return ItemStack.EMPTY;
		}
		
		ItemStack stack = new ItemStack(itemStackArray[index].getItem(), 
										itemStackArray[index].getCount(), 
										itemStackArray[index].getMetadata());
		stack.setTagCompound(itemStackArray[index].getTagCompound());

        return stack;
    }

	public boolean isTransparent() {
		return isTransparent;
	}

	public void setTransparent(boolean isTransparent) {
		this.isTransparent = isTransparent;
		BlockPipe.setState(isTransparent, world, pos);
		markDirty();
	}

	@Override
	public void readFromNBT(NBTTagCompound compound) {
		super.readFromNBT(compound);

		isTransparent = compound.getBoolean("isTransparent");
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		super.writeToNBT(compound);
		compound.setBoolean("isTransparent", isTransparent);

		return compound;
	}

	protected void notifyUpdate(BlockPos blockPos) {
		IBlockState state = world.getBlockState(blockPos);
		world.notifyBlockUpdate(blockPos, state, state, 3);
	}

	@Override
	public void onWrenchRightClicked(EntityPlayer player, ItemStack wrench) {
		setTransparent(!isTransparent);
	}
}
