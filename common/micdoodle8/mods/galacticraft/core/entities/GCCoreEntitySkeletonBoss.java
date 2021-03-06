package micdoodle8.mods.galacticraft.core.entities;

import java.util.List;
import java.util.Random;
import micdoodle8.mods.galacticraft.API.IDungeonBoss;
import micdoodle8.mods.galacticraft.API.IDungeonBossSpawner;
import micdoodle8.mods.galacticraft.API.IEntityBreathable;
import micdoodle8.mods.galacticraft.core.GalacticraftCore;
import micdoodle8.mods.galacticraft.core.items.GCCoreItems;
import micdoodle8.mods.galacticraft.core.network.GCCorePacketManager;
import micdoodle8.mods.galacticraft.core.tile.GCCoreTileEntityTreasureChest;
import micdoodle8.mods.galacticraft.core.util.PacketUtil;
import micdoodle8.mods.galacticraft.moon.wgen.dungeon.GCRoomBoss;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.stats.AchievementList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import universalelectricity.core.vector.Vector3;
import universalelectricity.prefab.network.IPacketReceiver;
import universalelectricity.prefab.network.PacketManager;
import com.google.common.io.ByteArrayDataInput;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Copyright 2012-2013, micdoodle8
 * 
 * All rights reserved.
 * 
 */
public class GCCoreEntitySkeletonBoss extends EntityMob implements IEntityBreathable, IDungeonBoss, IBossDisplayData, IPacketReceiver
{
    protected long ticks = 0;
    private static final ItemStack defaultHeldItem = new ItemStack(Item.bow, 1);
    private IDungeonBossSpawner spawner;

    public int throwTimer;
    public int postThrowDelay = 20;
    public Entity thrownEntity;
    public Entity targetEntity;
    public int deathTicks = 0;
    
    public int entitiesWithin;
    public int entitiesWithinLast;

    private Vector3 roomCoords;
    private Vector3 roomSize;

    public GCCoreEntitySkeletonBoss(World par1World)
    {
        super(par1World);
        this.setSize(1.5F, 4.0F);
        this.isImmuneToFire = true;
        this.texture = "/micdoodle8/mods/galacticraft/core/client/entities/skeletonboss.png";
        this.moveSpeed = 0.0F;
        this.tasks.addTask(4, new GCCoreEntityAIArrowAttack(this, this.moveSpeed, 1, 20));
        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        this.targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 32.0F, 0, true));
    }

    public GCCoreEntitySkeletonBoss(World world, Vector3 vec)
    {
        this(world);
        this.setPosition(vec.x, vec.y, vec.z);
    }

    public void setRoom(Vector3 coords, Vector3 size)
    {
        this.roomCoords = coords;
        this.roomSize = size;
    }

    @Override
    public boolean attackEntityFrom(DamageSource par1DamageSource, int par2)
    {
        PacketManager.sendPacketToClients(this.getDescriptionPacket(), this.worldObj, new Vector3(this), 100);
        return super.attackEntityFrom(par1DamageSource, par2);
    }

    @Override
    public void updateRiderPosition()
    {
        if (this.riddenByEntity != null)
        {
            if (!(this.riddenByEntity instanceof EntityPlayer) || !((EntityPlayer) this.riddenByEntity).func_71066_bF())
            {
                this.riddenByEntity.lastTickPosX = this.lastTickPosX;
                this.riddenByEntity.lastTickPosY = this.lastTickPosY + this.getMountedYOffset() + this.riddenByEntity.getYOffset();
                this.riddenByEntity.lastTickPosZ = this.lastTickPosZ;
            }

            final double offsetX = Math.sin(this.rotationYaw * Math.PI / 180.0D);
            final double offsetZ = Math.cos(this.rotationYaw * Math.PI / 180.0D);
            final double offsetY = 2 * Math.cos((this.throwTimer + this.postThrowDelay) * 0.05F);

            this.riddenByEntity.setPosition(this.posX + offsetX, this.posY + this.getMountedYOffset() + this.riddenByEntity.getYOffset() + offsetY, this.posZ + offsetZ);
        }
    }

    @Override
    public void knockBack(Entity par1Entity, int par2, double par3, double par5)
    {
        ;
    }

    @Override
    public void onCollideWithPlayer(EntityPlayer par1EntityPlayer)
    {
        if (this.riddenByEntity == null && this.postThrowDelay == 0 && this.throwTimer == 0 && par1EntityPlayer.equals(this.targetEntity) && this.deathTicks == 0)
        {
            if (!this.worldObj.isRemote)
            {
                PacketDispatcher.sendPacketToAllAround(this.posX, this.posY, this.posZ, 40.0, this.worldObj.provider.dimensionId, PacketUtil.createPacket(GalacticraftCore.CHANNEL, 25, new Object[] { 0 }));
                par1EntityPlayer.mountEntity(this);
            }

            this.throwTimer = 40;
        }

        super.onCollideWithPlayer(par1EntityPlayer);
    }

    @Override
    public boolean isAIEnabled()
    {
        return true;
    }

    @Override
    public int getMaxHealth()
    {
        return 150;
    }

    @Override
    public boolean canBePushed()
    {
        return false;
    }

    @Override
    protected String getLivingSound()
    {
        return "";
    }

    @Override
    protected String getHurtSound()
    {
        this.playSound("galacticraft.entity.bossliving", this.getSoundVolume(), this.getSoundPitch() + 6.0F);
        return "";
    }

    @Override
    protected String getDeathSound()
    {
        return "";
    }

    @Override
    protected void onDeathUpdate()
    {
        ++this.deathTicks;

        if (this.deathTicks >= 180 && this.deathTicks <= 200)
        {
            final float f = (this.rand.nextFloat() - 0.5F) * 1.5F;
            final float f1 = (this.rand.nextFloat() - 0.5F) * 2.0F;
            final float f2 = (this.rand.nextFloat() - 0.5F) * 1.5F;
            this.worldObj.spawnParticle("hugeexplosion", this.posX + f, this.posY + 2.0D + f1, this.posZ + f2, 0.0D, 0.0D, 0.0D);
        }

        int i;
        int j;

        if (!this.worldObj.isRemote)
        {
            if (this.deathTicks >= 180 && this.deathTicks % 5 == 0)
            {
                PacketDispatcher.sendPacketToAllAround(this.posX, this.posY, this.posZ, 40.0, this.worldObj.provider.dimensionId, PacketUtil.createPacket(GalacticraftCore.CHANNEL, 24, new Object[] { 0 }));
            }

            if (this.deathTicks > 150 && this.deathTicks % 5 == 0)
            {
                i = 30;

                while (i > 0)
                {
                    j = EntityXPOrb.getXPSplit(i);
                    i -= j;
                    this.worldObj.spawnEntityInWorld(new EntityXPOrb(this.worldObj, this.posX, this.posY, this.posZ, j));
                }
            }

            if (this.deathTicks == 1)
            {
                PacketDispatcher.sendPacketToAllAround(this.posX, this.posY, this.posZ, 40.0, this.worldObj.provider.dimensionId, PacketUtil.createPacket(GalacticraftCore.CHANNEL, 23, new Object[] { 0 }));
            }
        }

        this.moveEntity(0.0D, 0.10000000149011612D, 0.0D);
        this.renderYawOffset = this.rotationYaw += 20.0F;

        if (this.deathTicks == 200 && !this.worldObj.isRemote)
        {
            i = 2000;

            while (i > 0)
            {
                j = EntityXPOrb.getXPSplit(i);
                i -= j;
                this.worldObj.spawnEntityInWorld(new EntityXPOrb(this.worldObj, this.posX, this.posY, this.posZ, j));
            }

            for (final TileEntity tile : (List<TileEntity>) this.worldObj.loadedTileEntityList)
            {
                if (tile instanceof GCCoreTileEntityTreasureChest)
                {
                    final double d3 = tile.xCoord + 0.5D - this.posX;
                    final double d4 = tile.yCoord + 0.5D - this.posY;
                    final double d5 = tile.zCoord + 0.5D - this.posZ;
                    final double dSq = d3 * d3 + d4 * d4 + d5 * d5;

                    if (dSq < Math.pow(75.0D, 2))
                    {
                        final int amountOfGoodies = 3;

                        if (!((GCCoreTileEntityTreasureChest) tile).locked)
                        {
                            ((GCCoreTileEntityTreasureChest) tile).locked = true;
                        }

                        for (int gg = 0; gg < amountOfGoodies; gg++)
                        {
                            int attempts = 0;
                            for (int r = this.rand.nextInt(((IInventory) tile).getSizeInventory()); attempts < 200; this.rand.nextInt(((IInventory) tile).getSizeInventory()))
                            {
                                if (((IInventory) tile).getStackInSlot(r) == null)
                                {
                                    if (this.getGuaranteedLoot(gg, this.rand) != null)
                                    {
                                        ((IInventory) tile).setInventorySlotContents(r, this.getGuaranteedLoot(gg, this.rand));
                                        r = this.rand.nextInt(((IInventory) tile).getSizeInventory());
                                    }
                                    else
                                    {
                                        ((IInventory) tile).setInventorySlotContents(r, this.getLoot(this.rand));
                                        r = this.rand.nextInt(((IInventory) tile).getSizeInventory());
                                    }

                                    break;
                                }

                                attempts++;
                            }
                        }

                        break;
                    }
                }
            }

            this.entityDropItem(new ItemStack(GCCoreItems.key.itemID, 1, 0), 0.5F);

            super.setDead();

            if (this.spawner != null)
            {
                this.spawner.setBossDefeated(true);
                this.spawner.setBoss(null);
                this.spawner.setBossSpawned(false);
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ItemStack getHeldItem()
    {
        return GCCoreEntitySkeletonBoss.defaultHeldItem;
    }

    @Override
    public EnumCreatureAttribute getCreatureAttribute()
    {
        return EnumCreatureAttribute.UNDEAD;
    }

    @Override
    public void setDead()
    {
        if (this.spawner != null)
        {
            this.spawner.setBossDefeated(false);
            this.spawner.setBoss(null);
            this.spawner.setBossSpawned(false);
        }

        super.setDead();
    }

    @Override
    public void onLivingUpdate()
    {
        if (this.ticks >= Long.MAX_VALUE)
        {
            this.ticks = 1;
        }

        this.ticks++;

        final EntityPlayer player = this.worldObj.getClosestPlayer(this.posX, this.posY, this.posZ, 20.0);

        if (player != null && !player.equals(this.targetEntity))
        {
            if (this.getDistanceSqToEntity(player) < 400.0D)
            {
                final PathEntity pathentity = this.getNavigator().getPathToEntityLiving(player);
                this.targetEntity = player;
                this.getNavigator().setPath(pathentity, this.health >= 75.0 ? 0.2F : 0.35F);
                this.moveSpeed = 0.3F + (this.health >= this.getMaxHealth() / 2 ? 0.1F : 1.0F);
            }
        }
        else
        {
            this.targetEntity = null;
            this.moveSpeed = 0.0F;
        }

        final Vector3 thisVec = new Vector3(this);
        final List l = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, AxisAlignedBB.getBoundingBox(thisVec.x - 10, thisVec.y - 10, thisVec.z - 10, thisVec.x + 10, thisVec.y + 10, thisVec.z + 10));

        for (final Entity e : (List<Entity>) l)
        {
            if (e instanceof GCCoreEntitySkeletonBoss)
            {
                if (((GCCoreEntitySkeletonBoss) e).getHealth() >= this.health)
                {
                    ((GCCoreEntitySkeletonBoss) e).setDead();
                }
            }
        }

        if (this.throwTimer > 0)
        {
            this.throwTimer--;
        }

        if (this.postThrowDelay > 0)
        {
            this.postThrowDelay--;
        }

        new Vector3(this);

        if (this.roomCoords != null && this.roomSize != null)
        {
            List<Entity> entitiesWithin = this.worldObj.getEntitiesWithinAABB(EntityPlayer.class, AxisAlignedBB.getAABBPool().getAABB(this.roomCoords.intX() - 1, this.roomCoords.intY() - 1, this.roomCoords.intZ() - 1, this.roomCoords.intX() + this.roomSize.intX(), this.roomCoords.intY() + this.roomSize.intY(), this.roomCoords.intZ() + this.roomSize.intZ()));

            this.entitiesWithin = entitiesWithin.size();
            
            if (this.entitiesWithin == 0 && this.entitiesWithinLast != 0)
            {
                List<EntityPlayer> entitiesWithin2 = this.worldObj.getEntitiesWithinAABB(EntityPlayer.class, AxisAlignedBB.getAABBPool().getAABB(this.roomCoords.intX() - 11, this.roomCoords.intY() - 11, this.roomCoords.intZ() - 11, this.roomCoords.intX() + this.roomSize.intX() + 10, this.roomCoords.intY() + this.roomSize.intY() + 10, this.roomCoords.intZ() + this.roomSize.intZ() + 10));

                for (EntityPlayer p : entitiesWithin2)
                {
                    p.sendChatToPlayer("Boss despawned, don't leave the boss room while fighting! Re-enter room to respawn boss.");
                }
                
                this.setDead();
                
                if (this.spawner != null)
                {
                    this.spawner.setPlayerCheated();
                }
                
                return;
            }
            
            this.entitiesWithinLast = this.entitiesWithin;
        }

        if (this.riddenByEntity != null && this.throwTimer == 0)
        {
            this.postThrowDelay = 20;

            this.thrownEntity = this.riddenByEntity;

            if (!this.worldObj.isRemote)
            {
                this.riddenByEntity.mountEntity(this);
            }
        }

        if (this.thrownEntity != null && this.postThrowDelay == 18)
        {
            double d0 = this.posX - this.thrownEntity.posX;
            double d1;

            for (d1 = this.posZ - this.thrownEntity.posZ; d0 * d0 + d1 * d1 < 1.0E-4D; d1 = (Math.random() - Math.random()) * 0.01D)
            {
                d0 = (Math.random() - Math.random()) * 0.01D;
            }

            PacketDispatcher.sendPacketToAllAround(this.posX, this.posY, this.posZ, 40.0, this.worldObj.provider.dimensionId, PacketUtil.createPacket(GalacticraftCore.CHANNEL, 26, new Object[] { 0 }));

            ((EntityPlayer) this.thrownEntity).attackedAtYaw = (float) (Math.atan2(d1, d0) * 180.0D / Math.PI) - this.rotationYaw;

            this.thrownEntity.isAirBorne = true;
            final float f = MathHelper.sqrt_double(d0 * d0 + d1 * d1);
            final float f1 = 2.4F;
            this.thrownEntity.motionX /= 2.0D;
            this.thrownEntity.motionY /= 2.0D;
            this.thrownEntity.motionZ /= 2.0D;
            this.thrownEntity.motionX -= d0 / f * f1;
            this.thrownEntity.motionY += (double) f1 / 5;
            this.thrownEntity.motionZ -= d1 / f * f1;

            if (this.thrownEntity.motionY > 0.4000000059604645D)
            {
                this.thrownEntity.motionY = 0.4000000059604645D;
            }
        }

        if (this.ticks % 5 == 0)
        {
            PacketManager.sendPacketToClients(this.getDescriptionPacket(), this.worldObj, new Vector3(this), 100);
        }

        super.onLivingUpdate();
    }

    public Packet getDescriptionPacket()
    {
        return GCCorePacketManager.getPacket(GalacticraftCore.CHANNELENTITIES, this, this.health);
    }

    @Override
    public void handlePacketData(INetworkManager network, int packetType, Packet250CustomPayload packet, EntityPlayer player, ByteArrayDataInput dataStream)
    {
        try
        {
            if (this.worldObj.isRemote)
            {
                this.health = dataStream.readInt();
            }
        }
        catch (final Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void onDeath(DamageSource par1DamageSource)
    {
        super.onDeath(par1DamageSource);

        if (par1DamageSource.getSourceOfDamage() instanceof EntityArrow && par1DamageSource.getEntity() instanceof EntityPlayer)
        {
            final EntityPlayer var2 = (EntityPlayer) par1DamageSource.getEntity();
            final double var3 = var2.posX - this.posX;
            final double var5 = var2.posZ - this.posZ;

            if (var3 * var3 + var5 * var5 >= 2500.0D)
            {
                var2.triggerAchievement(AchievementList.snipeSkeleton);
            }
        }
    }

    @Override
    protected int getDropItemId()
    {
        return Item.arrow.itemID;
    }

    @Override
    protected void dropFewItems(boolean par1, int par2)
    {
    }

    @Override
    public EntityItem entityDropItem(ItemStack par1ItemStack, float par2)
    {
        final EntityItem entityitem = new EntityItem(this.worldObj, this.posX, this.posY + par2, this.posZ, par1ItemStack);
        entityitem.motionY = -2.0D;
        entityitem.delayBeforeCanPickup = 10;
        if (this.captureDrops)
        {
            this.capturedDrops.add(entityitem);
        }
        else
        {
            this.worldObj.spawnEntityInWorld(entityitem);
        }
        return entityitem;
    }

    @Override
    protected void dropRareDrop(int par1)
    {
        if (par1 > 0)
        {
            final ItemStack var2 = new ItemStack(Item.bow);
            EnchantmentHelper.addRandomEnchantment(this.rand, var2, 5);
            this.entityDropItem(var2, 0.0F);
        }
        else
        {
            this.dropItem(Item.bow.itemID, 1);
        }
    }

    @Override
    public boolean canBreath()
    {
        return true;
    }

    @Override
    public float getExperienceToSpawn()
    {
        return 50.0F;
    }

    @Override
    public double getDistanceToSpawn()
    {
        return 40.0D;
    }

    @Override
    public void onBossSpawned(IDungeonBossSpawner spawner)
    {
        this.spawner = spawner;
    }

    @Override
    public ItemStack getGuaranteedLoot(int loop, Random rand)
    {
        if (loop == 0)
        {
            switch (rand.nextInt(2))
            {
            case 0:
                return new ItemStack(GCCoreItems.schematic, 1, 0);
            case 1:
                return new ItemStack(GCCoreItems.schematic, 1, 1);
            }
        }

        return null;
    }

    @Override
    public ItemStack getLoot(Random rand)
    {
        final int r = rand.nextInt(3);

        switch (r)
        {
        case 0:
            return new ItemStack(GCCoreItems.lightOxygenTank, 1, GCCoreItems.lightOxygenTank.getMaxDamage() - rand.nextInt(GCCoreItems.lightOxygenTank.getMaxDamage() / 2) + 1);
        case 1:
            return new ItemStack(GCCoreItems.oilCanister, 1, GCCoreItems.oilCanister.getMaxDamage() - rand.nextInt(GCCoreItems.oilCanister.getMaxDamage() / 2) + 1);
        default:
            return new ItemStack(GCCoreItems.oilCanister, 1, 61);
        }
    }

    @Override
    public int getBossHealth()
    {
        return this.health;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound nbt)
    {
        super.writeEntityToNBT(nbt);
        nbt.setDouble("roomCoordsX", this.roomCoords.x);
        nbt.setDouble("roomCoordsY", this.roomCoords.y);
        nbt.setDouble("roomCoordsZ", this.roomCoords.z);
        nbt.setDouble("roomSizeX", this.roomSize.x);
        nbt.setDouble("roomSizeY", this.roomSize.y);
        nbt.setDouble("roomSizeZ", this.roomSize.z);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound nbt)
    {
        super.readEntityFromNBT(nbt);
        this.roomCoords = new Vector3();
        this.roomCoords.x = nbt.getDouble("roomCoordsX");
        this.roomCoords.y = nbt.getDouble("roomCoordsY");
        this.roomCoords.z = nbt.getDouble("roomCoordsZ");
        this.roomSize = new Vector3();
        this.roomSize.x = nbt.getDouble("roomSizeX");
        this.roomSize.y = nbt.getDouble("roomSizeY");
        this.roomSize.z = nbt.getDouble("roomSizeZ");
    }
}
