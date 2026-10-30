import com.evolvedwarfare.world.tower.TowerChecks;
import com.evolvedwarfare.world.tower.TowerDesigner;
import com.evolvedwarfare.world.tower.TowerPlan;

/** Vuelca torres de vigilancia como JSON (una por línea) para dibujarlas con tools/ew_towers.py. */
public class TowerDump {
	public static void main(String[] args) {
		String[] styles = args[0].split(",");
		int count = Integer.parseInt(args[1]);
		long first = args.length > 2 ? Long.parseLong(args[2]) : 1L;
		boolean snowy = args.length > 3 && args[3].equals("snow");
		for (String name : styles) {
			TowerDesigner.Style style = TowerDesigner.Style.valueOf(name);
			for (long seed = first; seed < first + count; seed++) {
				TowerPlan plan = TowerDesigner.design(seed * 0x9E3779B97F4A7C15L + style.ordinal(), style, snowy);
				int deck = TowerChecks.deck(plan);
				StringBuilder sb = new StringBuilder();
				sb.append("{\"style\":\"").append(name).append("\",\"seed\":").append(seed).append(",\"desc\":\"")
						.append(plan.description).append("\",\"reaches\":").append(TowerChecks.reachesTop(plan, deck))
						.append(",\"deck\":").append(deck).append(",\"guards\":[");
				boolean f = true;
				for (TowerPlan.Guard g : plan.guards()) {
					if (!f) sb.append(',');
					f = false;
					sb.append('[').append(g.x()).append(',').append(g.y()).append(',').append(g.z()).append(']');
				}
				sb.append("],\"loot\":").append(plan.lootSpots().size()).append(",\"blocks\":[");
				f = true;
				for (TowerPlan.Block b : plan.blocks()) {
					if (!f) sb.append(',');
					f = false;
					sb.append('[').append(b.x()).append(',').append(b.y()).append(',').append(b.z()).append(",\"").append(b.state())
							.append("\",").append(b.flags()).append(']');
				}
				sb.append("]}");
				System.out.println(sb);
			}
		}
	}
}
