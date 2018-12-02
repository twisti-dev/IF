package com.github.stefvanschie.inventoryframework.pane;

import com.github.stefvanschie.inventoryframework.Gui;
import com.github.stefvanschie.inventoryframework.GuiItem;
import com.github.stefvanschie.inventoryframework.exception.XMLLoadException;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A pane for panes that should be spread out over multiple pages
 */
public class PaginatedPane extends Pane {

    /**
     * A set of panes for the different pages
     */
    private final Map<Integer, List<Pane>> panes = new HashMap<>();

    /**
     * The current page
     */
    private int page;

    /**
     * {@inheritDoc}
     */
    public PaginatedPane(int x, int y, int length, int height, Priority priority) {
        super(x, y, length, height, priority);
    }

    /**
     * {@inheritDoc}
     */
    public PaginatedPane(int x, int y, int length, int height) {
        super(x, y, length, height);
    }

    /**
     * {@inheritDoc}
     */
    public PaginatedPane(int length, int height) {
        super(length, height);
    }

    /**
     * Returns the current page
     *
     * @return the current page
     */
    public int getPage() {
        return page;
    }

    /**
     * Returns the amount of pages
     *
     * @return the amount of pages
     */
    public int getPages() {
        return panes.size();
    }
    /**
     * Assigns a pane to a selected page
     *
     * @param page the page to assign the pane to
     * @param pane the new pane
     */
    public void addPane(int page, Pane pane) {
        if (!this.panes.containsKey(page))
            this.panes.put(page, new ArrayList<>());

        this.panes.get(page).add(pane);

        this.panes.get(page).sort(Comparator.comparing(Pane::getPriority));
    }

    /**
     * Sets the current displayed page
     *
     * @param page the page
     */
    public void setPage(int page) {
		if (!panes.containsKey(page))
			throw new ArrayIndexOutOfBoundsException("page outside range");
		this.page = page;
    }

	/**
	 * Populates the PaginatedPane based on the provided list by adding new pages until all items can fit.
	 * This can be helpful when dealing with lists of unknown size.
	 *
	 * @param items The list to populate the pane with
	 */
	@Contract("null -> fail")
	public void populateWithItemStacks(@NotNull List<ItemStack> items) {
		//Don't do anything if the list is empty
		if (items.isEmpty()) return;

		int itemsPerPage = this.height * this.length;
		int pagesNeeded = items.size() / itemsPerPage + 1;

		for (int i = 0; i < pagesNeeded; i++) {
			StaticPane page = new StaticPane(getX(), getY(), this.length, this.height);

			for (int j = 0; j < itemsPerPage; j++) {
				//Check if the loop reached the end of the list
				int index = i * itemsPerPage + j;
				if (index >= items.size()) break;

				//Convert j to x and y
				int x = j % (this.length);
				int y = j / (this.length);

				page.addItem(new GuiItem(items.get(index)), x, y);
			}
			this.addPane(i, page);
		}
	}

	/**
	 * This method creates a list of ItemStacks all with the given {@code material} and the display names.
	 * After that it calls {@link #populateWithItemStacks(List)}
	 * This method also translates the color char {@code &} for all names.
	 *
	 * @param displayNames The display names for all the items
	 * @param material The material to use for the {@link org.bukkit.inventory.ItemStack}s
	 */
	@Contract("null, _ -> fail")
	public void populateWithNames(@NotNull List<String> displayNames, Material material) {
		if(material == null || material == Material.AIR) return;

		populateWithItemStacks(displayNames.stream().map((name) -> {
			ItemStack itemStack = new ItemStack(material);
			ItemMeta itemMeta = itemStack.getItemMeta();
			itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
			itemStack.setItemMeta(itemMeta);
			return itemStack;
		}).collect(Collectors.toList()));
	}

    /**
     * {@inheritDoc}
     */
    @Override
    public void display(Inventory inventory, int paneOffsetX, int paneOffsetY, int maxLength, int maxHeight) {
        this.panes.get(page).forEach(pane -> pane.display(inventory, paneOffsetX + getX(),
                paneOffsetY + getY(), Math.min(length, maxLength), Math.min(height, maxHeight)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean click(@NotNull InventoryClickEvent event, int paneOffsetX, int paneOffsetY, int maxLength,
                         int maxHeight) {
        int length = Math.min(this.length, maxLength);
        int height = Math.min(this.height, maxHeight);

        int slot = event.getSlot();

        int x = (slot % 9) - getX();
        int y = (slot / 9) - getY();

        if (x < 0 || x >= length || y < 0 || y >= height)
            return false;

        if (onLocalClick != null)
            onLocalClick.accept(event);

        boolean success = false;

        for (Pane pane : this.panes.get(page))
            success = success || pane.click(event, paneOffsetX + getX(), paneOffsetY + getY(),
                length, height);

        return success;
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Contract(pure = true)
    @Override
    public Collection<Pane> getPanes() {
        Collection<Pane> panes = new HashSet<>();

        this.panes.forEach((integer, p) -> {
            p.forEach(pane -> panes.addAll(pane.getPanes()));
            panes.addAll(p);
        });

        return panes;
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Contract(pure = true)
    @Override
    public Collection<GuiItem> getItems() {
        return getPanes().stream().flatMap(pane -> pane.getItems().stream()).collect(Collectors.toList());
    }

    /**
     * Loads a paginated pane from a given element
     *
     * @param instance the instance class
     * @param element the element
     * @return the paginated pane
     */
    @Nullable
    @Contract("_, null -> fail")
    public static PaginatedPane load(Object instance, @NotNull Element element) {
        try {
            PaginatedPane paginatedPane = new PaginatedPane(
                Integer.parseInt(element.getAttribute("length")),
                Integer.parseInt(element.getAttribute("height"))
            );

            Pane.load(paginatedPane, instance, element);

            if (element.hasAttribute("populate"))
                return paginatedPane;

            int pageCount = 0;

            NodeList childNodes = element.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node item = childNodes.item(i);

                if (item.getNodeType() != Node.ELEMENT_NODE)
                    continue;

                NodeList innerNodes = item.getChildNodes();

                List<Pane> panes = new ArrayList<>();

                for (int j = 0; j < innerNodes.getLength(); j++) {
                    Node pane = innerNodes.item(j);

                    if (pane.getNodeType() != Node.ELEMENT_NODE)
                        return null;

                    Pane innerPane = Gui.loadPane(instance, pane);

                    if (innerPane == null) {
                        throw new XMLLoadException("Unable to load paginated pane: inner pane " + j + " could not be loaded.");
                    }

                    panes.add(innerPane);
                }

                for (Pane pane : panes)
                    paginatedPane.addPane(pageCount, pane);

                pageCount++;
            }

            return paginatedPane;
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        return null;
    }

}
