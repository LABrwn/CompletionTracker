package de.labrwn.completiontracker.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Starts the real game in GitHub Actions, creates a world, collects a few things and takes
 * screenshots of every page of the tracker at two GUI scales.
 */
public class TrackerClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.getInput().resizeWindow(1920, 1080);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("give @p minecraft:diamond 3");
			sp.getServer().runCommand("give @p minecraft:red_wool 2");
			sp.getServer().runCommand("give @p minecraft:golden_apple 1");
			sp.getServer().runCommand("advancement grant @p only minecraft:adventure/adventuring_time minecraft:plains");
			sp.getServer().runCommand("advancement grant @p only minecraft:story/mine_stone");
			sp.getServer().runCommand("execute as @p at @s run summon minecraft:sheep ~3 ~ ~ {Color:14b,NoAI:1b}");
			sp.getServer().runCommand("execute as @p at @s run summon minecraft:cow ~-3 ~ ~ {NoAI:1b}");
			ctx.waitTicks(10);
			sp.getServer().runCommand("execute as @p run damage @e[type=minecraft:sheep,limit=1,sort=nearest] 100 minecraft:player_attack by @s");
			sp.getServer().runCommand("execute as @p run damage @e[type=minecraft:cow,limit=1,sort=nearest] 100 minecraft:player_attack by @s");
			ctx.waitTicks(140); // tracker scans every second, advancements every five

			for (int scale : new int[]{3, 4}) {
				ctx.runOnClient(client -> {
					client.options.guiScale().set(scale);
					client.resizeGui();
				});
				ctx.waitTicks(2);
				shot(ctx, 0, null, null, "s" + scale + "_1_overview");
				shot(ctx, 1, "m:minecraft:sheep", null, "s" + scale + "_2_mobs_sheep");
				shot(ctx, 2, "i:minecraft:diamond", null, "s" + scale + "_3_items");
				shot(ctx, 3, "a:minecraft:adventure/adventuring_time", null, "s" + scale + "_4_advancements");
				shot(ctx, 4, null, null, "s" + scale + "_5_chronicle");
				shot(ctx, 2, null, "sort", "s" + scale + "_6_dropdown");
				ctx.setScreen(() -> null);
				ctx.waitTicks(2);
			}
		}
	}

	private static void shot(ClientGameTestContext ctx, int tab, String select, String dropdown, String name) {
		ctx.setScreen(() -> null);
		ctx.waitTick();
		TrackerScreen.debugShow(tab, select, dropdown);
		ctx.setScreen(TrackerScreen::new);
		ctx.waitTicks(5);
		ctx.takeScreenshot(name);
	}
}
