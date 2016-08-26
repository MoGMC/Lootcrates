package com.plofcorp.Lootcrates;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import com.vexsoftware.votifier.model.VotifierEvent;

import net.milkbowl.vault.economy.Economy;

public class Main extends JavaPlugin implements Listener {

	// TODO:
	// - drop items that were not taken out of crate on close
	// - player heads?

	public Random rand = new Random();
	public List<String> itemsCommon;
	public List<String> itemsRare;

	// for some reason vault needs it to be static
	public static Economy econ = null;

	// lootcrate menu
	public String menuTitle;

	// lootcrate item "icon"c
	public ItemStack lootcrateItem;

	// max items
	int maxItemsCommon;
	int maxItemsRare;

	int maxMoney;

	@EventHandler
	public void onVote(VotifierEvent e) {
		vote(e.getVote().getUsername());

	}

	public void vote(String playerName) {

		@SuppressWarnings("deprecation")
		Player target = Bukkit.getPlayer(playerName);

		if (target == null) {
			return;

		}

		target.getInventory().addItem(lootcrateItem);

		target.sendMessage(ChatColor.YELLOW + "Thank you for voting! One Lootcrate has been added to your inventory.");

	}

	// if there is only one plugin command no need to check the name of it
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String list, String[] args) {

		if (args.length < 1) {
			sender.sendMessage(String.format("%sThis server is running Lootcrate version %s!",
					ChatColor.YELLOW.toString(), getDescription().getVersion()));
			return true;

		}

		if (args[0].equalsIgnoreCase("give")) {

			if (args.length < 2) {
				sender.sendMessage(
						ChatColor.RED + "Not enough arguments!" + ChatColor.AQUA + " /lootcrate give <player>");
				return true;
			}

			if (!sender.hasPermission("lootcrates.give")) {
				return false;

			}

			vote(args[1]);

			return true;

		} else if (args[0].equalsIgnoreCase("reload")) {

			if (!sender.hasPermission("lootcrates.reload")) {
				return false;

			}

			try {

				loadExternalConfig();

				sender.sendMessage(ChatColor.YELLOW + "Reloaded Lootcrate loot config!");

			} catch (IOException e) {

				sender.sendMessage(ChatColor.RED + "Could not reload loot config. Check console for errors.");

				e.printStackTrace();

			}

			return true;

		}

		return false;

	}

	@EventHandler
	public void onRightClick(PlayerInteractEvent evt) {

		if (!(evt.getAction().equals(Action.RIGHT_CLICK_AIR) || evt.getAction().equals(Action.RIGHT_CLICK_BLOCK)
				|| evt.getAction().equals(Action.LEFT_CLICK_AIR) || evt.getAction().equals(Action.LEFT_CLICK_BLOCK))) {
			return;

		}

		ItemStack item = evt.getItem();

		if (item == null) {
			return;

		}

		if (!item.isSimilar(lootcrateItem)) {
			return;

		}

		int amount = item.getAmount() - 1;

		if (amount < 1) {
			evt.getPlayer().getInventory().remove(evt.getItem());

		} else {
			item.setAmount(amount);

		}

		evt.setCancelled(true);

		evt.getPlayer().openInventory(lootcrateInventory());

	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent evt) {

		if (!evt.getInventory().getTitle().equals(menuTitle)) {
			return;

		}

		ItemStack item = evt.getCurrentItem();

		if (item == null) {
			return;

		}

		if (item.getType().equals(Material.GOLD_INGOT)) {

			cashMoney((Player) evt.getWhoClicked(), item);

			evt.setCancelled(true);

			evt.getInventory().remove(item);

		}

	}

	// cashes in money and drops items that aren't used/taken
	@EventHandler
	public void onInventoryClose(InventoryCloseEvent e) {

		if (!e.getInventory().getTitle().equals(menuTitle)) {
			return;

		}

		Player player = (Player) e.getPlayer();

		World world = player.getWorld();
		Location loc = player.getLocation();

		for (ItemStack i : e.getInventory()) {

			if (i == null) {
				continue;

			}

			if (i.getType().equals(Material.GOLD_INGOT)) {

				cashMoney(player, i);

				e.getInventory().remove(i);

				continue;

			}

			world.dropItemNaturally(loc, i);

		}

	}

	public Inventory lootcrateInventory() {

		Inventory inv = Bukkit.createInventory(null, 27, menuTitle);
		
		ItemStack moneyItem = fromMarkup("266;");
		inv.setItem(rand.nextInt(27), moneyItem);

		for (int i = 0; i < maxItemsCommon; i++) {

			int randItemIndex = rand.nextInt(itemsCommon.size());

			ItemStack item = fromMarkup(itemsCommon.get(randItemIndex));

			int randPosition = rand.nextInt(27);

			while (inv.getItem(randPosition) != null) {
				randPosition = rand.nextInt(27);

			}

			inv.setItem(randPosition, item);

		}

		for (int i = 0; i < maxItemsRare; i++) {

			int randItemIndex = rand.nextInt(itemsRare.size());

			ItemStack item = fromMarkup(itemsRare.get(randItemIndex));

			int randPosition = rand.nextInt(27);

			while (inv.getItem(randPosition) != null) {
				randPosition = rand.nextInt(27);

			}

			inv.setItem(randPosition, item);
		}

		return inv;

	}

	// IMPORTANT: does not remove money.
	public void cashMoney(Player player, ItemStack item) {

		ItemMeta meta = item.getItemMeta();

		// prevent people from naming things "$100" and getting free money
		if (!meta.getDisplayName().startsWith(ChatColor.GOLD.toString())) {
			return;

		}

		String amount = meta.getDisplayName().substring(5, meta.getDisplayName().length());

		int amountMoney = Integer.parseInt(amount);

		econ.depositPlayer(player, amountMoney);

		player.sendMessage(ChatColor.YELLOW + "Cashed $" + amountMoney + "!");

	}

	public void loadExternalConfig() throws IOException {

		URL website = new URL(getConfig().getString("loot_config_url"));

		ReadableByteChannel rbc = Channels.newChannel(website.openStream());

		File file = new File("config.yml");

		FileOutputStream fos = new FileOutputStream(file);

		fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

		fos.close();

		YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

		menuTitle = colorString(config.getString("lootcrate.menu_title"));

		lootcrateItem = generateLootcrateIcon(config.getConfigurationSection("lootcrate"));

		itemsCommon = config.getStringList("loot.items.common");
		itemsRare = config.getStringList("loot.items.rare");

		maxItemsCommon = config.getInt("loot.items.amount_common");
		maxItemsRare = config.getInt("loot.items.amount_rare");

		maxMoney = config.getInt("maximum_money");

	}

	/* on enable stuff */

	public void onEnable() {

		saveDefaultConfig();

		// IMPORTANT: make sure to have a service provider like essentials

		econ = getServer().getServicesManager().getRegistration(Economy.class).getProvider();

		try {

			loadExternalConfig();

		} catch (IOException e) {

			Bukkit.getLogger().severe("Could not fetch loot config from URL. Check config. Disabling plugin...");
			Bukkit.getPluginManager().disablePlugin(this);

			e.printStackTrace();

		}

		this.getServer().getPluginManager().registerEvents(this, this);

	}

	// idk why this is a seperate method but oh well
	public ItemStack generateLootcrateIcon(ConfigurationSection lootcrateSection) {

		@SuppressWarnings("deprecation")
		ItemStack lootcrate = new ItemStack(Material.getMaterial(lootcrateSection.getInt("item")));

		ItemMeta meta = lootcrate.getItemMeta();

		meta.setDisplayName(colorString(lootcrateSection.getString("name")));
		meta.setLore(colorList(lootcrateSection.getStringList("lore")));

		lootcrate.setItemMeta(meta);

		return lootcrate;

	}

	/* util */

	// markup - data[end marker]
	// example: item ID's end marker is ; - "266;" would set the item's id to
	// 266

	@SuppressWarnings("deprecation")
	public ItemStack fromMarkup(String markup) {

		ItemStack item = new ItemStack(Material.DIRT);

		StringBuilder b = new StringBuilder();

		for (char c : markup.toCharArray()) {

			switch (c) {

			case ';':

				item = new ItemStack(Integer.valueOf(b.toString()));
				b.setLength(0);

				break;

			case ',':

				item.setAmount(rand.nextInt(Integer.valueOf(b.toString())) + 1);
				b.setLength(0);

				break;

			case ')':

				String[] ench = b.toString().split("\\|");

				item.addEnchantment(Enchantment.getById(Integer.valueOf(ench[0])), Integer.valueOf(ench[1]));

				b.setLength(0);

				break;

			case ':':

				item.setDurability(Short.valueOf(b.toString()));
				b.setLength(0);

				break;

			case ']':

				ItemMeta meta = item.getItemMeta();
				meta.setDisplayName(colorString(b.toString()));

				item.setItemMeta(meta);

				b.setLength(0);

				break;

			default:

				b.append(c);

			}

		}

		if (item.getType() == Material.GOLD_INGOT) {
			ItemMeta meta = item.getItemMeta();
			int money = rand.nextInt(maxMoney);
			while (money < 100) {
				money = rand.nextInt(maxMoney);
			}
			meta.setDisplayName(ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + "$" + money);
			item.setItemMeta(meta);

		}

		return item;

	}

	public List<String> colorList(List<String> list) {

		ArrayList<String> colored = new ArrayList<String>();

		for (String s : list) {
			colored.add(colorString(s));

		}

		return colored;

	}

	// shorter and can change formatting to various other symbols faster if
	// needed
	public String colorString(String string) {

		return ChatColor.translateAlternateColorCodes('&', string);

	}

}
