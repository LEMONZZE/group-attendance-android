package ru.groupjournal.attendance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

public final class AttendanceBookTest {
    public static void main(String[] args) throws Exception {
        LocalDate first = LocalDate.of(2026, 11, 23);
        LocalDate next = first.plusDays(1);
        LocalDate later = next.plusDays(1);
        LocalDate before = first.minusDays(2);

        AttendanceBook book = new AttendanceBook();
        expect(0, book.students(first).size());
        book.add(first, Arrays.asList("  Блюдник В. П.  ", "Аксёнов\u00a0И.И.", "Анейчик П.В."));
        expect("Аксёнов И.И.", book.students(first).get(0).name);
        expect("Блюдник В. П.", book.students(first).get(2).name);
        expect(3, book.students(first).size());
        expect("Все присутствуют.", book.report(first));

        long aks = book.students(first).get(0).id;
        long an = book.students(first).get(1).id;
        long bl = book.students(first).get(2).id;
        book.setPresent(first, aks, false);
        expect("Отсутствует 1 чел.: Аксёнов.", book.report(first));
        expect(2, book.students(first).size() - book.absent(first).size());
        book.setPresent(first, an, false);
        book.setPresent(first, bl, false);
        String original = "Отсутствуют 3 чел.: Аксёнов, Анейчик, Блюдник.";
        expect(original, book.report(first));
        expect("Все присутствуют.", book.report(next));

        book.setPresent(next, aks, false);
        book.setPresent(next, an, false);
        book.rename(next, aks, "Антонов И.И.");
        book.remove(next, an);
        book.add(next, Collections.singletonList("Гусев М.А."));
        expect(original, book.report(first));
        expect("Отсутствует 1 чел.: Антонов.", book.report(next));
        expect("Все присутствуют.", book.report(later));
        expect(3, book.students(later).size());

        expect(0, book.students(before).size());
        book.importPastRoster(before, next);
        book.setPresent(before, aks, false);
        expect(0, book.students(before.plusDays(1)).size());
        expect("Отсутствует 1 чел.: Антонов.", book.report(before));
        book.rename(later, aks, "Артёмов И.И.");
        expect("Отсутствует 1 чел.: Антонов.", book.report(before));

        AttendanceBook changed = book.copy();
        changed.setPresent(first, aks, true);
        expect("Отсутствуют 2 чел.: Анейчик, Блюдник.", changed.report(first));
        expect(original, book.report(first));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        book.write(new DataOutputStream(bytes));
        AttendanceBook reloaded = AttendanceBook.read(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        expect(original, reloaded.report(first));
        expect("Отсутствует 1 чел.: Антонов.", reloaded.report(next));
        expect("Отсутствует 1 чел.: Антонов.", reloaded.report(before));
        expect("Артёмов И.И.", reloaded.students(later).get(0).name);
        reloaded.setPresent(next, aks, true);
        expect("Все присутствуют.", reloaded.report(next));
        reloaded.add(later, Collections.singletonList("Юдин П.И."));
        expect(true, reloaded.students(later).get(3).id > bl);

        try {
            reloaded.add(later, Arrays.asList("Чистов В.Е.", "   "));
            throw new AssertionError("Expected invalid name to fail");
        } catch (IllegalArgumentException expected) {
            expect(4, reloaded.students(later).size());
        }
        try {
            AttendanceBook.read(new DataInputStream(new ByteArrayInputStream(new byte[4])));
            throw new AssertionError("Expected invalid format to fail");
        } catch (EOFException e) {
            throw new AssertionError("Expected format error instead of EOF", e);
        } catch (java.io.IOException expected) {
            // Invalid data is not mistaken for an empty journal.
        }
        System.out.println("AttendanceBook: все проверки пройдены.");
    }

    private static void expect(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", got: " + actual);
        }
    }
}
