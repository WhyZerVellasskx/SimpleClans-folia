package net.sacredlabyrinth.phaed.simpleclans.ui;

import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.RankPermission;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.events.ComponentClickEvent;
import net.sacredlabyrinth.phaed.simpleclans.managers.PermissionsManager;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.ConfirmationFrame;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.WarningFrame;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.COMMANDS_CLAN;

/**
 * Handles inventory interactions for SCFrame components.
 *
 * @author RoinujNosde
 */
public class InventoryController implements Listener {
	private static final Map<UUID, SCFrame> frames = new HashMap<>();

	@EventHandler(ignoreCancelled = true)
	public void onClose(InventoryCloseEvent event) {
		HumanEntity entity = event.getPlayer();
		if (entity instanceof Player) {
			frames.remove(entity.getUniqueId());
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onInteract(InventoryClickEvent event) {
		HumanEntity entity = event.getWhoClicked();
		if (!(entity instanceof Player)) return;

		Player player = (Player) entity;
		SCFrame frame = frames.get(player.getUniqueId());
		if (frame == null) return;

		event.setCancelled(true);

		if (event.getClickedInventory() == null || event.getClickedInventory().getType() == InventoryType.PLAYER) {
			return;
		}

		SCComponent component = frame.getComponent(event.getSlot());
		if (component == null) return;

		ClickType click = event.getClick();
		Runnable listener = component.getListener(click);
		if (listener == null) return;

		if (component.isVerifiedOnly(click) && !isClanVerified(player)) {
			InventoryDrawer.open(new WarningFrame(frame, player, null));
			return;
		}

		Object permission = component.getPermission(click);
		if (permission != null && !hasPermission(player, permission)) {
			InventoryDrawer.open(new WarningFrame(frame, player, permission));
			return;
		}

		if (component.isConfirmationRequired(click)) {
			listener = () -> InventoryDrawer.open(new ConfirmationFrame(frame, player, component.getListener(click)));
		}

		Runnable finalListener = listener;
		SimpleClans.getScheduler().execute(() -> {
			Optional.ofNullable(event.getCurrentItem()).ifPresent(currentItem -> {
				ComponentClickEvent componentClickEvent = new ComponentClickEvent(player, frame, component);
				Bukkit.getPluginManager().callEvent(componentClickEvent);
				if (componentClickEvent.isCancelled()) return;

				ItemMeta itemMeta = currentItem.getItemMeta();
				if (itemMeta != null) {
					itemMeta.setLore(Collections.singletonList(lang("gui.loading", player)));
					currentItem.setItemMeta(itemMeta);
				}

				finalListener.run();
			});
		});
	}

	/**
	 * Checks if the Player's Clan is verified.
	 *
	 * @param player the Player
	 * @return true if the Clan is verified
	 */
	private boolean isClanVerified(@NotNull Player player) {
		ClanPlayer cp = SimpleClans.getInstance().getClanManager().getAnyClanPlayer(player.getUniqueId());
		return cp != null && cp.getClan() != null && cp.getClan().isVerified();
	}

	/**
	 * Checks if the player has the permission.
	 *
	 * @param player the Player
	 * @param permission the permission
	 * @return true if they have permission
	 */
	private boolean hasPermission(@NotNull Player player, @NotNull Object permission) {
		PermissionsManager pm = SimpleClans.getInstance().getPermissionsManager();
		if (permission instanceof String) {
			String perms = (String) permission;
			boolean leaderPerm = perms.contains("simpleclans.leader") && !perms.equalsIgnoreCase("simpleclans.leader.create");
			ClanPlayer cp = SimpleClans.getInstance().getClanManager().getAnyClanPlayer(player.getUniqueId());

			return pm.has(player, perms) && (!leaderPerm || (cp != null && cp.isLeader()));
		}
		return pm.has(player, (RankPermission) permission, false);
	}

	/**
	 * Registers the frame in the InventoryController.
	 *
	 * @param frame the frame
	 */
	public static void register(@NotNull SCFrame frame) {
		frames.put(frame.getViewer().getUniqueId(), frame);
	}

	/**
	 * Checks if the Player is registered.
	 *
	 * @param player the Player
	 * @return true if they are registered
	 */
	public static boolean isRegistered(@NotNull Player player) {
		return frames.containsKey(player.getUniqueId());
	}

	/**
	 * Runs a subcommand for the Player.
	 *
	 * @param player the Player
	 * @param subcommand the subcommand
	 * @param update whether to update the inventory instead of closing
	 */
	public static void runSubcommand(@NotNull Player player, @NotNull String subcommand, boolean update, String... args) {
		SimpleClans plugin = SimpleClans.getInstance();
		String baseCommand = plugin.getSettingsManager().getString(COMMANDS_CLAN);
		String finalCommand = String.format("%s %s %s", baseCommand, subcommand, String.join(" ", args));

		SimpleClans.getScheduler().execute(player.getLocation(), () -> {
			player.performCommand(finalCommand);

			if (!update) {
				player.closeInventory();
			} else {
				SCFrame currentFrame = frames.get(player.getUniqueId());
				if (currentFrame instanceof ConfirmationFrame) {
					currentFrame = currentFrame.getParent();
				}
				InventoryDrawer.open(currentFrame);
			}
		});
	}
}
