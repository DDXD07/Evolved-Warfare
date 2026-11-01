import com.evolvedwarfare.world.city.CityChecks;
import com.evolvedwarfare.world.city.CityDesigner;
import com.evolvedwarfare.world.city.CityPlan;

/** Vuelca ciudades como JSON (una por línea) para dibujarlas con tools/ew_cities.py. */
public class CityDump {
	public static void main(String[] args) {
		String[] styles = args[0].split(",");
		int count = Integer.parseInt(args[1]);
		long first = args.length > 2 ? Long.parseLong(args[2]) : 1L;
		boolean blocks = args.length <= 3 || !args[3].equals("noblocks");
		for (String name : styles) {
			CityDesigner.Style style = CityDesigner.Style.valueOf(name);
			for (long seed = first; seed < first + count; seed++) {
				long t0 = System.nanoTime();
				CityPlan plan = CityDesigner.design(seed * 0x9E3779B97F4A7C15L + style.ordinal(), style);
				long ms = (System.nanoTime() - t0) / 1_000_000;
				StringBuilder sb = new StringBuilder();
				sb.append("{\"style\":\"").append(name).append("\",\"seed\":").append(seed).append(",\"desc\":\"").append(plan.description)
						.append("\",\"ms\":").append(ms).append(",\"hx\":").append(plan.halfX).append(",\"hz\":").append(plan.halfZ)
						.append(",\"stranded\":").append(CityChecks.strandedTraders(plan)).append(",\"closed\":")
						.append(CityChecks.closedGates(plan)).append(",\"count\":").append(plan.blocks().size()).append(",\"traders\":[");
				boolean f = true;
				for (CityPlan.Trader t : plan.traders()) {
					if (!f) sb.append(',');
					f = false;
					sb.append("[\"").append(t.type()).append("\",").append(t.x()).append(',').append(t.y()).append(',').append(t.z()).append(',')
							.append(t.bedX()).append(',').append(t.bedY()).append(',').append(t.bedZ()).append(']');
				}
				sb.append("],\"guards\":[");
				f = true;
				for (CityPlan.Guard g : plan.guards()) {
					if (!f) sb.append(',');
					f = false;
					sb.append("[\"").append(g.role()).append("\",").append(g.x()).append(',').append(g.y()).append(',').append(g.z()).append(']');
				}
				sb.append("],\"residents\":").append(plan.residents().size()).append(",\"blocks\":[");
				if (blocks) {
					f = true;
					for (CityPlan.Block b : plan.blocks()) {
						if (!f) sb.append(',');
						f = false;
						sb.append('[').append(b.x()).append(',').append(b.y()).append(',').append(b.z()).append(",\"").append(b.state())
								.append("\",").append(b.flags()).append(']');
					}
				}
				sb.append("]}");
				System.out.println(sb);
			}
		}
	}
}
