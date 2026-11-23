package ru.groupjournal.attendance;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class AttendanceBook {
    static final int MAX_STUDENTS = 500;
    private static final int MAGIC = 0x47524131; // GRA1
    private static final Comparator<Student> BY_NAME =
            Comparator.comparing((Student s) -> s.name, Collator.getInstance(new Locale("ru")))
                    .thenComparingLong(s -> s.id);

    static final class Student {
        final long id;
        final String name;

        Student(long id, String name) {
            this.id = id;
            this.name = name;
        }

        String surname() {
            int space = name.indexOf(' ');
            return space < 0 ? name : name.substring(0, space);
        }
    }

    private long nextId = 1;
    // Roster edits apply from their date onward; explicit past imports apply to one day only.
    private final TreeMap<LocalDate, List<Student>> rosterVersions = new TreeMap<>();
    private final Map<LocalDate, List<Student>> pastImports = new HashMap<>();
    private final Map<LocalDate, Set<Long>> absences = new HashMap<>();

    AttendanceBook copy() {
        AttendanceBook copy = new AttendanceBook();
        copy.nextId = nextId;
        copy.rosterVersions.putAll(rosterVersions);
        copy.pastImports.putAll(pastImports);
        copy.absences.putAll(absences);
        return copy;
    }

    private List<Student> regularRoster(LocalDate date) {
        Map.Entry<LocalDate, List<Student>> version = rosterVersions.floorEntry(date);
        return version == null ? Collections.emptyList() : version.getValue();
    }

    List<Student> students(LocalDate date) {
        List<Student> imported = pastImports.get(date);
        return imported != null ? imported : regularRoster(date);
    }

    private void setRoster(LocalDate date, List<Student> roster) {
        roster.sort(BY_NAME);
        rosterVersions.put(date, Collections.unmodifiableList(roster));
        // If the clock was set back to a previously imported day, edits to today's
        // regular roster take precedence.
        pastImports.remove(date);
    }

    static String cleanName(String name) {
        String result = name.replaceAll("[\\p{Z}\\s]+", " ").trim();
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Введи фамилию и инициалы.");
        }
        if (result.length() > 80) {
            throw new IllegalArgumentException("В одной строке должно быть не больше 80 символов.");
        }
        return result;
    }

    void add(LocalDate date, List<String> names) {
        List<Student> roster = new ArrayList<>(regularRoster(date));
        if (names.isEmpty()) {
            throw new IllegalArgumentException("Введи хотя бы одну фамилию.");
        }
        if (names.size() > MAX_STUDENTS - roster.size()) {
            throw new IllegalArgumentException("В списке может быть не больше " + MAX_STUDENTS + " человек.");
        }
        long id = nextId;
        for (String name : names) {
            roster.add(new Student(id++, cleanName(name)));
        }
        setRoster(date, roster);
        nextId = id;
    }

    void rename(LocalDate date, long id, String name) {
        String clean = cleanName(name);
        List<Student> roster = new ArrayList<>(regularRoster(date));
        for (int i = 0; i < roster.size(); i++) {
            if (roster.get(i).id == id) {
                roster.set(i, new Student(id, clean));
                setRoster(date, roster);
                return;
            }
        }
    }

    void remove(LocalDate date, long id) {
        List<Student> roster = new ArrayList<>(regularRoster(date));
        if (roster.removeIf(s -> s.id == id)) {
            setRoster(date, roster);
            setPresent(date, id, true);
        }
    }

    void importPastRoster(LocalDate date, LocalDate today) {
        if (date.isBefore(today) && students(date).isEmpty() && !students(today).isEmpty()) {
            pastImports.put(date, students(today));
        }
    }

    boolean present(LocalDate date, long id) {
        return !absences.getOrDefault(date, Collections.emptySet()).contains(id);
    }

    void setPresent(LocalDate date, long id, boolean isPresent) {
        Set<Long> changed = new HashSet<>(absences.getOrDefault(date, Collections.emptySet()));
        if (isPresent) {
            changed.remove(id);
        } else {
            changed.add(id);
        }
        if (changed.isEmpty()) {
            absences.remove(date);
        } else {
            absences.put(date, Collections.unmodifiableSet(changed));
        }
    }

    List<Student> absent(LocalDate date) {
        List<Student> result = new ArrayList<>();
        for (Student student : students(date)) {
            if (!present(date, student.id)) result.add(student);
        }
        return result;
    }

    String report(LocalDate date) {
        List<Student> absent = absent(date);
        if (absent.isEmpty()) return "Все присутствуют.";
        List<String> names = new ArrayList<>();
        for (Student student : absent) names.add(student.surname());
        String verb = absent.size() == 1 ? "Отсутствует " : "Отсутствуют ";
        return verb + absent.size() + " чел.: " + String.join(", ", names) + ".";
    }

    void write(DataOutput out) throws IOException {
        out.writeInt(MAGIC);
        out.writeLong(nextId);
        writeRosters(out, rosterVersions);
        writeRosters(out, pastImports);
        out.writeInt(absences.size());
        for (Map.Entry<LocalDate, Set<Long>> entry : absences.entrySet()) {
            out.writeLong(entry.getKey().toEpochDay());
            out.writeInt(entry.getValue().size());
            for (long id : entry.getValue()) out.writeLong(id);
        }
    }

    static AttendanceBook read(DataInput in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException("Unsupported attendance file");
        AttendanceBook result = new AttendanceBook();
        result.nextId = in.readLong();
        if (result.nextId < 1) throw new IOException("Invalid student ID");
        readRosters(in, result.rosterVersions);
        readRosters(in, result.pastImports);
        for (int i = count(in, 500_000); i > 0; i--) {
            LocalDate date = LocalDate.ofEpochDay(in.readLong());
            Set<Long> ids = new HashSet<>();
            for (int n = count(in, MAX_STUDENTS); n > 0; n--) ids.add(in.readLong());
            result.absences.put(date, Collections.unmodifiableSet(ids));
        }
        return result;
    }

    private static void writeRosters(DataOutput out, Map<LocalDate, List<Student>> rosters)
            throws IOException {
        out.writeInt(rosters.size());
        for (Map.Entry<LocalDate, List<Student>> entry : rosters.entrySet()) {
            out.writeLong(entry.getKey().toEpochDay());
            out.writeInt(entry.getValue().size());
            for (Student student : entry.getValue()) {
                out.writeLong(student.id);
                out.writeUTF(student.name);
            }
        }
    }

    private static void readRosters(DataInput in, Map<LocalDate, List<Student>> rosters)
            throws IOException {
        for (int i = count(in, 500_000); i > 0; i--) {
            LocalDate date = LocalDate.ofEpochDay(in.readLong());
            List<Student> students = new ArrayList<>();
            for (int n = count(in, MAX_STUDENTS); n > 0; n--) {
                students.add(new Student(in.readLong(), in.readUTF()));
            }
            rosters.put(date, Collections.unmodifiableList(students));
        }
    }

    private static int count(DataInput in, int limit) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > limit) throw new IOException("Invalid attendance record count");
        return count;
    }
}
