package com.voicepacks.source;

import com.voicepacks.trigger.EventSink;
import com.voicepacks.trigger.EventType;
import com.voicepacks.trigger.GameEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * LOOT events: NPC drops (core LootManager) and non-NPC loot such as raid chests, Barrows and clue caskets (the
 * Loot Tracker's LootReceived, EVENT type only, since NPC kills arrive through NpcLootReceived already). Fields:
 * source, item (every item name, so a pack can match any one), top (the most valuable item) and value (total GE
 * value). The journey store adds record / sourceRecord.
 */
public class LootSource
{
	@Inject
	private ItemManager itemManager;

	@Inject
	private EventSink sink;

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		emit(npc == null ? null : npc.getName(), event.getItems());
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getType() == LootRecordType.EVENT)
		{
			emit(event.getName(), event.getItems());
		}
	}

	private void emit(String source, Collection<ItemStack> items)
	{
		if (source == null || items.isEmpty())
		{
			return;
		}
		List<String> names = new ArrayList<>();
		long total = 0;
		long topValue = -1;
		String top = null;
		for (ItemStack stack : items)
		{
			String name = itemManager.getItemComposition(stack.getId()).getName();
			long value = (long) itemManager.getItemPrice(stack.getId()) * stack.getQuantity();
			names.add(name);
			total += value;
			if (value > topValue)
			{
				topValue = value;
				top = name;
			}
		}
		sink.accept(new GameEvent(EventType.LOOT).with("source", source).with("item", names).with("top", top)
			.with("value", total));
	}
}
