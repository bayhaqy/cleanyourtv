package io.github.bayhaqy.reminders;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** The reminders, as one JSON array in the app's private preferences (backups are off). */
final class Store {
    private Store() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("reminders", Context.MODE_PRIVATE);
    }

    static List<Reminder> load(Context c) {
        List<Reminder> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Reminder r = new Reminder();
                r.id = o.getLong("id");
                r.label = o.optString("label");
                r.hour = o.getInt("hour");
                r.minute = o.getInt("minute");
                r.repeat = o.optInt("repeat", Reminder.ONCE);
                r.weekday = o.optInt("weekday", r.weekday);
                r.date = o.optInt("date");
                r.enabled = o.optBoolean("enabled", true);
                out.add(r);
            }
        } catch (JSONException ignored) {
            // A damaged list starts empty rather than crashing the alarm path.
        }
        return out;
    }

    static void save(Context c, List<Reminder> list) {
        JSONArray a = new JSONArray();
        try {
            for (Reminder r : list) {
                a.put(new JSONObject().put("id", r.id).put("label", r.label).put("hour", r.hour).put("minute", r.minute)
                        .put("repeat", r.repeat).put("weekday", r.weekday).put("date", r.date).put("enabled", r.enabled));
            }
        } catch (JSONException ignored) {
            return;
        }
        prefs(c).edit().putString("list", a.toString()).apply();
    }

    static Reminder find(Context c, long id) {
        for (Reminder r : load(c)) if (r.id == id) return r;
        return null;
    }

    /** Adds or replaces by id, saves and schedules. */
    static void put(Context c, Reminder r) {
        if (r.id == 0) {
            long next = prefs(c).getLong("next_id", 1);
            r.id = next;
            prefs(c).edit().putLong("next_id", next + 1).apply();
        }
        List<Reminder> list = load(c);
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == r.id) {
                list.set(i, r);
                replaced = true;
            }
        }
        if (!replaced) list.add(r);
        save(c, list);
        Scheduler.schedule(c, r);
    }

    static void delete(Context c, long id) {
        List<Reminder> list = load(c);
        list.removeIf(r -> r.id == id);
        save(c, list);
        Scheduler.cancel(c, id);
    }
}
