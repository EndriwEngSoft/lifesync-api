package com.lifesync.api.ai.service;

import com.lifesync.api.ai.dto.UserContextDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Troca indices curtos (T1, H1, G1) por titulos em textos livres escritos pelo
 * modelo: o cliente da API nao conhece os indices.
 */
final class IndexText {

    private static final Pattern INDEX_TOKEN = Pattern.compile("\\b([THG][0-9]{1,3})\\b");

    private IndexText() {
    }

    static Map<String, String> titlesByIndex(UserContextDTO context) {
        Map<String, String> titles = new HashMap<>();
        context.tasks().forEach(t -> titles.put(t.shortIndex(), t.title()));
        context.habits().forEach(h -> titles.put(h.shortIndex(), h.name()));
        context.goals().forEach(g -> titles.put(g.shortIndex(), g.title()));
        return titles;
    }

    /**
     * Se o modelo citar um indice (T1) num texto livre, troca pelo titulo do
     * item. Indice que nao existe fica como esta. O que esta dentro de um
     * titulo citado pelo modelo tambem fica: um titulo como "prova G1" contem
     * algo com cara de indice, mas e' texto do usuario.
     */
    static String humanize(String text, Map<String, String> titlesByIndex) {
        List<int[]> quotedTitles = titleRanges(text, titlesByIndex.values());
        Matcher matcher = INDEX_TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String title = titlesByIndex.get(matcher.group(1));
            boolean keep = title == null || isInside(matcher.start(), quotedTitles);
            matcher.appendReplacement(out, Matcher.quoteReplacement(keep ? matcher.group(1)
                    : "\"" + title + "\""));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Intervalos [inicio, fim) do texto ocupados por citacoes literais dos titulos do contexto. */
    private static List<int[]> titleRanges(String text, Iterable<String> titles) {
        List<int[]> ranges = new ArrayList<>();
        for (String title : titles) {
            if (title == null || title.isBlank()) {
                continue;
            }
            for (int at = text.indexOf(title); at >= 0; at = text.indexOf(title, at + title.length())) {
                ranges.add(new int[] {at, at + title.length()});
            }
        }
        return ranges;
    }

    private static boolean isInside(int position, List<int[]> ranges) {
        for (int[] range : ranges) {
            if (position >= range[0] && position < range[1]) {
                return true;
            }
        }
        return false;
    }
}
