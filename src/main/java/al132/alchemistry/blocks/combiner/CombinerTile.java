package al132.alchemistry.blocks.combiner;

import al132.alchemistry.Config;
import al132.alchemistry.Ref;
import al132.alchemistry.blocks.AlchemistryBaseTile;
import al132.alchemistry.recipes.CombinerRecipe;
import al132.alib.tiles.CustomEnergyStorage;
import al132.alib.tiles.CustomStackHandler;
import al132.alib.tiles.EnergyTile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CombinerTile extends AlchemistryBaseTile implements EnergyTile {

    public static TileEntityType<CombinerTile> type;

    public CombinerRecipe currentRecipe = null;
    public boolean recipeIsLocked = false;
    int progressTicks = 0;
    public boolean paused = false;
    public CustomStackHandler clientRecipeTarget;


    public CombinerTile() {
        super(Ref.combinerTile);
        clientRecipeTarget = new CustomStackHandler(this, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return false;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }
        };
    }

    public void updateRecipe() {
        currentRecipe = CombinerRecipe.matchInputs(this.getInput());
    }

    @Override
    public void tick() {
        if (world.isRemote) return;
        //this.energy.receiveEnergy(50, false);
        ItemStack displayStack = ItemStack.EMPTY;
        if (currentRecipe != null && currentRecipe.getOutput() != null)
            displayStack = currentRecipe.getOutput().getStack().copy();
        if (recipeIsLocked) clientRecipeTarget.setStackInSlot(0, displayStack);
        if (!this.paused && canProcess()) process();
        this.markDirtyClient();
    }

    public boolean canProcess() {
        return currentRecipe != null
                //&& (currentRecipe.gamestage == "" || hasCurrentRecipeStage()) TODO
                && energy.getEnergyStored() >= Config.COMBINER_ENERGY_PER_TICK.get()//ConfigHandler.combinerEnergyPerTick!! //has enough energy
                && (currentRecipe.getOutput().getCount() + getOutput().getStackInSlot(0).getCount() <= currentRecipe.getOutput().getMaxStackSize()) //output quantities can stack
                && (ItemStack.areItemsEqual(getOutput().getStackInSlot(0), currentRecipe.getOutput()) || getOutput().getStackInSlot(0).isEmpty()) //output item types can stack
                && currentRecipe.matchesHandlerStacks(this.getInput())
                && (!recipeIsLocked || ItemStack.areItemStacksEqual(CombinerRecipe.matchInputs(getInput()).getOutput(), currentRecipe.getOutput()));
    }

    public void process() {
        this.energy.extractEnergy(Config.COMBINER_ENERGY_PER_TICK.get(), false);

        if (progressTicks < Config.COMBINER_TICKS_PER_OPERATION.get()) progressTicks++;
        else {
            progressTicks = 0;
            if (currentRecipe != null) {
                getOutput().setOrIncrement(0, currentRecipe.getOutput().copy());
            }
            if (currentRecipe != null && currentRecipe.inputs.size() == 9) {
                CombinerRecipe thisRecipe = currentRecipe;
                for (int index = 0; index < 9; index++) {
                    //if (currentRecipe != null) {
                        ItemStack stack = thisRecipe.inputs.get(index);
                        if (stack != null && !stack.isEmpty()) {
                            getInput().decrementSlot(index, stack.getCount());
                        }
                        if (getInput().getStackInSlot(index).getItem() == Ref.slotFiller) {
                            getInput().decrementSlot(index, 1);
                        }
                    //}
                }
            }
        }
    }

    @Override
    public void read(CompoundNBT compound) {
        super.read(compound);
        this.recipeIsLocked = compound.getBoolean("recipeIsLocked");
        this.progressTicks = compound.getInt("progressTicks");
        this.paused = compound.getBoolean("paused");
        // clientRecipeTarget.deserializeNBT(compound.getCompound("recipeTarget"));
        if (recipeIsLocked) {
            this.currentRecipe = CombinerRecipe.matchOutput(ItemStack.read(compound.getCompound("recipeTarget")));
            ItemStack temp = ItemStack.EMPTY;
            if (currentRecipe != null) temp = currentRecipe.getOutput().copy();
            clientRecipeTarget.setStackInSlot(0, temp);
        } else {
            this.updateRecipe();
            clientRecipeTarget.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT compound) {
        compound.putBoolean("recipeIsLocked", recipeIsLocked);
        compound.putInt("progressTicks", progressTicks);
        compound.putBoolean("paused", paused);
        compound.put("recipeTarget", clientRecipeTarget.getStackInSlot(0).serializeNBT());
        return super.write(compound);
    }

    @Nullable
    @Override
    public Container createMenu(int i, PlayerInventory playerInv, PlayerEntity player) {
        return new CombinerContainer(i, world, pos, playerInv, player);
    }

    @Override
    public CustomStackHandler initInput() {
        return new CustomStackHandler(this, 9) {

            @Nonnull
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                if (!recipeIsLocked) return super.isItemValid(slot, stack);
                else if (recipeIsLocked
                        && currentRecipe != null
                        && !currentRecipe.inputs.isEmpty()
                        && (ItemStack.areItemsEqual(stack, currentRecipe.inputs.get(slot)))) {
                    return super.isItemValid(slot, stack);
                } else return false;
            }

            @Override
            public void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                if (!recipeIsLocked) updateRecipe();
            }
        };
    }

    @Override
    public CustomStackHandler initOutput() {
        return new CustomStackHandler(this, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return false;
            }
        };
    }

    @Override
    public IEnergyStorage initEnergy() {
        return new CustomEnergyStorage(Config.COMBINER_ENERGY_CAPACITY.get());
    }

    @Override
    public IEnergyStorage getEnergy() {
        return energy;
    }
}