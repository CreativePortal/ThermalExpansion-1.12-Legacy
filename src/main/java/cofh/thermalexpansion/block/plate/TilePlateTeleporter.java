package cofh.thermalexpansion.block.plate;

import cofh.api.transport.IEnderDestination;
import cofh.api.transport.RegistryEnderAttuned;
import cofh.core.network.PacketCoFHBase;
import cofh.core.network.PacketHandler;
import cofh.core.util.SocialRegistry;
import cofh.lib.util.helpers.EntityHelper;
import cofh.thermalexpansion.gui.client.plate.GuiPlateTeleport;
import cofh.thermalexpansion.gui.container.ContainerTEBase;
import com.mojang.authlib.GameProfile;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.registry.GameRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityFireworkSparkFX;
import net.minecraft.client.particle.EntityPortalFX;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

public class TilePlateTeleporter extends TilePlatePoweredBase implements IEnderDestination {

	public static void initialize() {

		GameRegistry.registerTileEntity(TilePlateTeleporter.class, "cofh.thermalexpansion.PlateTeleporter");
	}

	protected static final int TELEPORT_COST = 100000;
	protected static final int DIMENSION_TELEPORT_COST = 1000000;
	protected static final int PARTICLE_DELAY = 80;
	protected static final int TELEPORT_DELAY = PARTICLE_DELAY + 50;

	protected int frequency = -1;
	protected int destination = -1;

	protected boolean isActive;

	public TilePlateTeleporter() {

		super(BlockPlate.Types.POWERED_TRANSLOCATE, 1000000);
	}

	protected void teleportEntity(Entity ent) {

	}

	@Override
	public void onEntityCollidedWithBlock(Entity theEntity) {

		if (theEntity.worldObj.isRemote || theEntity.timeUntilPortal > TELEPORT_DELAY) {
			theEntity.timeUntilPortal = theEntity.getPortalCooldown() + TELEPORT_DELAY;
			return;
		}

		if (destination == -1 || !RegistryEnderAttuned.hasDestination(this)) {
			return;
		}

		int teleportCost = TELEPORT_COST;
		IEnderDestination dest = RegistryEnderAttuned.getDestination(this);
		if (dest.dimension() != dimension()) {
			teleportCost = DIMENSION_TELEPORT_COST;
		}

		if (storage.getEnergyStored() < teleportCost) {
			return;
		}

		Class<? extends Entity> comp = Entity.class;
		if (!getAccess().isPublic()) {
			comp = EntityPlayer.class;
		}

		if (!comp.isInstance(theEntity) || !RegistryEnderAttuned.hasDestination(this)) {
			return;
		}

		l: if (!getAccess().isPublic()) {
			o: if (theEntity instanceof EntityItem) {
				String name = ((EntityItem) theEntity).func_145800_j();
				if (name == null) {
					break o;
				}
				if (getAccess().isRestricted() && SocialRegistry.playerHasAccess(name, getOwner())) {
					break l;
				}
				GameProfile i = MinecraftServer.getServer().func_152358_ax().func_152655_a(name);
				if (getOwner().getId().equals(i.getId())) {
					break l;
				}
			} else if (canPlayerAccess((EntityPlayer) theEntity)) {
				break l;
			}
			return;
		}

		if (theEntity instanceof EntityLivingBase) {
			if (theEntity.timeUntilPortal++ <= TELEPORT_DELAY) {
				if (!(theEntity instanceof EntityPlayerMP)) {
					theEntity.timeUntilPortal++;
				}
				World world = theEntity.worldObj;
				int i = theEntity.timeUntilPortal >= TELEPORT_DELAY ? 100 : 99;
				double x = theEntity.posX, z = theEntity.posZ, y = theEntity.posY;
				y += theEntity.height * .75;
				int amt = theEntity.timeUntilPortal * 5 / PARTICLE_DELAY;
				l: if (i == 100 || amt != ((theEntity.timeUntilPortal - 2) * 5 / PARTICLE_DELAY)) {
					if (i != 100 && theEntity.timeUntilPortal > PARTICLE_DELAY) {
						break l;
					}
					PacketCoFHBase packet = getModePacket();
					packet.addByte(i);
					if (i == 99)
						packet.addInt(amt);
					packet.addFloat((float) x);
					packet.addFloat((float) y);
					packet.addFloat((float) z);
					if (i == 100) {
						packet.addInt(theEntity.getEntityId());
						packet.addInt(dest.x());
						packet.addInt(dest.y());
						packet.addInt(dest.z());
						packet.addInt(dest.dimension());
					}
					TargetPoint targ = new TargetPoint(world.provider.dimensionId, xCoord, yCoord, zCoord, 50);
					PacketHandler.sendToAllAround(packet, targ);
				}
				if (i == 99)
					return;
			}
			theEntity.timeUntilPortal = theEntity.getPortalCooldown() + TELEPORT_DELAY;
		}

		if (storage.extractEnergy(teleportCost, false) == teleportCost) {
			if (dest.dimension() != dimension()) {
				EntityHelper.transferEntityToDimension(theEntity, dest.dimension(), MinecraftServer.getServer()
					.getConfigurationManager());
			}
			theEntity.setPositionAndRotation(dest.x() + .5, dest.y() + .2, dest.z() + .5,
				theEntity.rotationYaw, theEntity.rotationPitch);
		}
	}

	protected void addZapParticles(int time, double x, double y, double z) {

		time += 2;
		double dv = time / 2;

		for (int i = time; i-- > 0;) {
			for (int k = time; k-- > 0;) {
				double yV = Math.cos(k * Math.PI / dv), xV, zV;
				xV = Math.pow(Math.sin(i * Math.PI / dv) * yV, 1) * 2;
				zV = Math.pow(Math.cos(i * Math.PI / dv) * yV, 1) * 2;
				yV = Math.pow(Math.sin(k * Math.PI / dv) * 1., 1) * 2;
				EntityFX spark = new EntityPortalFX(worldObj, x, y, z, xV, yV, zV);
				Minecraft.getMinecraft().effectRenderer.addEffect(spark);
			}
		}
	}

	protected void addTeleportParticles(double x, double y, double z, boolean trail) {

		for (int i = 15; i-- > 0;) {
			for (int k = 15; k-- > 0;) {
				double yV = Math.cos(k * Math.PI / 7.5), xV, zV;
				xV = Math.pow(Math.sin(i * Math.PI / 7.5) * yV, 3) * .15;
				zV = Math.pow(Math.cos(i * Math.PI / 7.5) * yV, 3) * .15;
				yV = Math.pow(Math.sin(k * Math.PI / 7.5) * 1., 3) * .15;
				EntityFireworkSparkFX spark = new EntityFireworkSparkFX(worldObj,
						x, y, z, xV, yV, zV, Minecraft.getMinecraft().effectRenderer) {

					@Override
					public void moveEntity(double x, double y, double z) {

						motionY += 0.004;
						super.moveEntity(x, y + 0.004, z);
					}
				};
				spark.setTrail(trail);
				spark.setColour(0xE54CFF);
				spark.setFadeColour(0x750C9F);
				Minecraft.getMinecraft().effectRenderer.addEffect(spark);
			}
		}
	}

	@Override
	protected void handleModePacket(PacketCoFHBase payload) {

		super.handleModePacket(payload);

		byte type = payload.getByte();

		switch (type) {
		case 99:
			addZapParticles(payload.getInt(), payload.getFloat(), payload.getFloat(), payload.getFloat());
			break;
		case 100:
			float x = payload.getFloat(), y = payload.getFloat(), z = payload.getFloat();
			Entity ent = worldObj.getEntityByID(payload.getInt());
			addTeleportParticles(x, y, z, ent instanceof EntityEnderman);
			x = payload.getInt() + .5f;
			y = payload.getInt() + .2f;
			z = payload.getInt() + .5f;
			int dim = payload.getInt();
			if (ent != null) {
				if (dim != dimension() && !(ent instanceof EntityPlayer)) {
					ent.setDead();
				} else {
					ent.setPosition(x, y, z);
				}
			}
			break;
		}
	}

	@Override
	public String getChannelString() {

		return access.isPublic() ? "_public_" : owner.getName();
	}

	@Override
	public int getFrequency() {

		return frequency;
	}

	@Override
	public boolean setFrequency(int frequency) {

		if (!access.isPublic()) {
			return false;
		}
		if (frequency != this.frequency) {
			RegistryEnderAttuned.removeDestination(this);
			int old = this.frequency;
			this.frequency = frequency;
			isActive = frequency != -1;
			if (isActive) {
				if (RegistryEnderAttuned.hasDestination(this, false)) {
					this.frequency = old;
					return false;
				}
				RegistryEnderAttuned.addDestination(this);
			}

			markDirty();
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
		return true;
	}

	@Override
	public boolean clearFrequency() {

		return setFrequency(-1);
	}

	@Override
	public int getDestination() {

		return destination;
	}

	@Override
	public boolean setDestination(int frequency) {

		if (!access.isPublic()) {
			return false;
		}
		if (frequency != destination) {
			int old = destination;
			destination = frequency;
			if (destination != -1) {
				if (!RegistryEnderAttuned.hasDestination(this, false)) {
					// TODO: ???
					destination = old;
					return false;
				}
			}

			markDirty();
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
		return true;
	}

	@Override
	public boolean clearDestination() {

		return setDestination(-1);
	}

	@Override
	protected boolean readPortableTagInternal(EntityPlayer player, NBTTagCompound tag) {

		if (!getChannelString().equals(tag.getString("Channel"))) {
			// TODO: log message
			return false;
		}
		setDestination(tag.getInteger("Destination"));
		return true;
	}

	@Override
	protected boolean writePortableTagInternal(EntityPlayer player, NBTTagCompound tag) {

		tag.setString("Channel", getChannelString());
		tag.setInteger("Destination", destination);
		return true;
	}

	/* NBT METHODS */
	@Override
	public void readFromNBT(NBTTagCompound nbt) {

		super.readFromNBT(nbt);

		setFrequency(nbt.getInteger("Frequency"));
		setDestination(nbt.getInteger("Destination"));

	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {

		super.writeToNBT(nbt);

		nbt.setInteger("Frequency", frequency);
		nbt.setInteger("Destination", destination);
	}

	@Override
	public boolean isNotValid() {

		return !inWorld || this.tileEntityInvalid;
	}

	@Override
	public int x() {

		return xCoord;
	}

	@Override
	public int y() {

		return yCoord;
	}

	@Override
	public int z() {

		return zCoord;
	}

	@Override
	public int dimension() {

		return worldObj.provider.dimensionId;
	}

	/* GUI METHODS */
	@Override
	public Object getGuiClient(InventoryPlayer inventory) {

		return new GuiPlateTeleport(inventory, this);
	}

	@Override
	public Object getGuiServer(InventoryPlayer inventory) {

		return new ContainerTEBase(inventory, this, false, false);
	}

}
