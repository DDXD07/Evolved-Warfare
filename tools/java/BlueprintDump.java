import com.evolvedwarfare.building.Blueprint;
import com.evolvedwarfare.building.BlueprintGenerator;

/** Vuelca planos como JSON (uno por línea) para dibujarlos con tools/ew_buildings.py. */
public class BlueprintDump {
	public static void main(String[] args) {
		String[] professions = args[0].split(",");
		int seeds = Integer.parseInt(args[1]);
		int first = args.length > 2 ? Integer.parseInt(args[2]) : 1;
		for (String profession : professions) {
			for (int seed = first; seed < first + seeds; seed++) {
				Blueprint b = BlueprintGenerator.generate(profession, seed);
				StringBuilder sb = new StringBuilder();
				sb.append("{\"profession\":\"").append(profession).append("\",\"seed\":").append(seed).append(",\"materials\":{");
				boolean firstM = true;
				for (var e : b.materials().entrySet()) {
					if (!firstM) sb.append(',');
					firstM = false;
					sb.append('"').append(e.getKey().id()).append("\":").append(e.getValue());
				}
				sb.append("},\"pieces\":[");
				boolean firstP = true;
				for (Blueprint.Piece p : b.pieces()) {
					if (!firstP) sb.append(',');
					firstP = false;
					sb.append('[').append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append(',')
							.append(p.state() == null ? "null" : "\"" + p.state() + "\"").append(",\"").append(p.material().id())
							.append("\",").append(p.flags()).append(']');
				}
				sb.append("]}");
				System.out.println(sb);
			}
		}
	}
}
