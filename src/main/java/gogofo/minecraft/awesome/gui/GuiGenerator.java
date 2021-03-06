package gogofo.minecraft.awesome.gui;

import gogofo.minecraft.awesome.AwesomeMod;
import gogofo.minecraft.awesome.gui.features.FeatureControls;
import gogofo.minecraft.awesome.gui.features.FeatureUpgrades;
import gogofo.minecraft.awesome.inventory.ContainerFuser;
import gogofo.minecraft.awesome.inventory.ContainerGenerator;
import gogofo.minecraft.awesome.tileentity.TileEntityGenerator;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.util.ResourceLocation;

public class GuiGenerator extends AwesomeGui {
	
	private final TileEntityGenerator tileEntityGenerator;
	private InventoryPlayer playerInventory;
	
    public GuiGenerator(InventoryPlayer playerInventory, 
    					TileEntityGenerator tileEntityGenerator)
    {
        super(new ContainerGenerator(playerInventory, tileEntityGenerator),
        	  playerInventory, 
        	  tileEntityGenerator);
        
        this.playerInventory = playerInventory;
        this.tileEntityGenerator = tileEntityGenerator;

		addFeature(new FeatureControls(this, tileEntityGenerator));
		addFeature(new FeatureUpgrades(this, inventorySlots));
    }

	@Override
	protected void drawCustomGui() {
		drawSlotsByCustomContainer(inventorySlots);
        drawArrow(80, 38);
        drawFilledArrow(80, 38, tileEntityGenerator.getCurrentBurntPercent());
	}
}
