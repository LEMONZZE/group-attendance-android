package ru.groupjournal.attendance;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.AtomicFile;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;
    private static final LocalDate FIRST_DATE = LocalDate.of(1900, 1, 1);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ru"));

    private AttendanceBook book;
    private AtomicFile store;
    private LocalDate selectedDate;
    private LocalDate lastToday;
    private boolean rosterTab;
    private boolean dark;
    private int pageColor, surfaceColor, textColor, mutedColor, lineColor;
    private int accent, primary, onPrimary, softAccent, green, softGreen, red, softRed;
    private LinearLayout root;
    private LinearLayout body;
    private ScrollView scroll;
    private TextView presentCount;
    private TextView absentCount;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        lastToday = LocalDate.now();
        selectedDate = state == null ? lastToday
                : LocalDate.ofEpochDay(state.getLong("day", lastToday.toEpochDay()));
        if (selectedDate.isAfter(lastToday)) selectedDate = lastToday;
        rosterTab = state != null && state.getBoolean("roster");
        setupPalette();
        store = new AtomicFile(new File(getNoBackupFilesDir(), "journal.dat"));
        try {
            book = loadBook();
        } catch (IOException | DateTimeException e) {
            Log.e("Attendance", "Could not read journal", e);
            new AlertDialog.Builder(this)
                    .setTitle("Не удалось открыть журнал")
                    .setMessage("Сохранённые данные не были изменены.")
                    .setCancelable(false)
                    .setPositiveButton("Закрыть", (dialog, which) -> finish())
                    .show();
            return;
        }
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalDate today = LocalDate.now();
        if (book != null && !today.equals(lastToday)) {
            if (selectedDate.equals(lastToday) || selectedDate.isAfter(today)) selectedDate = today;
            lastToday = today;
            render();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putLong("day", selectedDate.toEpochDay());
        state.putBoolean("roster", rosterTab);
    }

    private AttendanceBook loadBook() throws IOException {
        try (DataInputStream input =
                     new DataInputStream(new BufferedInputStream(store.openRead()))) {
            return AttendanceBook.read(input);
        } catch (FileNotFoundException e) {
            if (store.getBaseFile().exists()) throw e;
            return new AttendanceBook();
        }
    }

    private boolean updateBook(Consumer<AttendanceBook> edit) {
        AttendanceBook updated = book.copy();
        edit.accept(updated);
        FileOutputStream file = null;
        try {
            file = store.startWrite();
            DataOutputStream output = new DataOutputStream(new BufferedOutputStream(file));
            updated.write(output);
            output.flush();
            store.finishWrite(file);
            book = updated;
            return true;
        } catch (IOException e) {
            if (file != null) store.failWrite(file);
            Log.e("Attendance", "Could not write journal", e);
            Toast.makeText(this, "Не удалось сохранить. Проверь свободное место.", Toast.LENGTH_LONG)
                    .show();
            return false;
        }
    }

    private void setupPalette() {
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        pageColor = dark ? 0xFF111624 : 0xFFF4F6FB;
        surfaceColor = dark ? 0xFF1B2234 : Color.WHITE;
        textColor = dark ? 0xFFEFF2FF : 0xFF18233B;
        mutedColor = dark ? 0xFFAFB9D1 : 0xFF52617B;
        lineColor = dark ? 0xFF2D374E : 0xFFE4E9F3;
        accent = dark ? 0xFFABC1FF : 0xFF264ABF;
        primary = dark ? 0xFFABC1FF : 0xFF264ABF;
        onPrimary = dark ? 0xFF142A66 : Color.WHITE;
        softAccent = dark ? 0xFF27375F : 0xFFE7EEFF;
        green = dark ? 0xFF9CE2C1 : 0xFF14644B;
        softGreen = dark ? 0xFF1B3A36 : 0xFFE4F5EE;
        red = dark ? 0xFFFFBBAE : 0xFF9E372F;
        softRed = dark ? 0xFF432C35 : 0xFFFFEEE9;
    }

    private void render() {
        root = column();
        root.setBackgroundColor(pageColor);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return WindowInsets.CONSUMED;
            });
        }
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            // The window's decor is available after setContentView.
            WindowInsetsController bars = getWindow().getInsetsController();
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            if (bars != null) bars.setSystemBarsAppearance(dark ? 0 : mask, mask);
        }
        root.requestApplyInsets();

        LinearLayout header = column();
        header.setPadding(dp(20), dp(16), dp(20), dp(10));
        TextView title = label("Посещаемость", 28, textColor, true);
        heading(title);
        header.addView(title);
        addAt(header, label("Журнал группы", 14, mutedColor, false), 2);

        LinearLayout tabs = row();
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(shape(surfaceColor, 16, lineColor));
        Button attendance = tab("Посещаемость", !rosterTab);
        Button roster = tab("Список группы", rosterTab);
        tabs.addView(attendance, weighted());
        LinearLayout.LayoutParams rosterParams = weighted();
        rosterParams.leftMargin = dp(4);
        tabs.addView(roster, rosterParams);
        attendance.setOnClickListener(v -> showTab(false));
        roster.setOnClickListener(v -> showTab(true));
        addAt(header, tabs, 16);
        root.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(20), dp(2), dp(20), dp(12));
        body = column();
        scroll.addView(body, new FrameLayout.LayoutParams(MATCH, WRAP));
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        if (rosterTab) renderRoster();
        else renderAttendance();
    }

    private Button tab(String text, boolean selected) {
        Button button = button(text, selected ? softAccent : surfaceColor,
                selected ? accent : mutedColor, 12);
        button.setSelected(selected);
        return button;
    }

    private void showTab(boolean roster) {
        if (rosterTab == roster) return;
        if (!roster && selectedDate.equals(lastToday)) selectedDate = LocalDate.now();
        rosterTab = roster;
        render();
    }

    private void selectDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.isAfter(today) || date.isBefore(FIRST_DATE) || selectedDate.equals(date)) return;
        lastToday = today;
        selectedDate = date;
        render();
    }

    private void showDatePicker() {
        LocalDate today = LocalDate.now();
        DatePickerDialog dialog = new DatePickerDialog(this,
                (picker, y, m, d) -> selectDate(LocalDate.of(y, m + 1, d)),
                selectedDate.getYear(), selectedDate.getMonthValue() - 1,
                selectedDate.getDayOfMonth());
        dialog.getDatePicker().setMaxDate(
                today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        dialog.show();
    }

    private void renderAttendance() {
        LocalDate today = LocalDate.now();
        boolean isToday = selectedDate.equals(today);
        List<AttendanceBook.Student> students = book.students(selectedDate);
        LinearLayout dateCard = row();
        dateCard.setPadding(dp(8), dp(8), dp(8), dp(8));
        dateCard.setBackground(shape(surfaceColor, 18, lineColor));
        Button previous = arrow("‹", "Предыдущий день", selectedDate.isAfter(FIRST_DATE));
        Button next = arrow("›", "Следующий день", selectedDate.isBefore(today));
        String dateText = (isToday ? "Сегодня\n" : "") + DATE_FORMAT.format(selectedDate);
        Button date = button(dateText, surfaceColor, textColor, 12);
        date.setContentDescription(dateText.replace('\n', ' ') + ". Выбрать дату");
        date.setMinHeight(dp(54));
        date.setOnClickListener(v -> showDatePicker());
        previous.setOnClickListener(v -> selectDate(selectedDate.minusDays(1)));
        next.setOnClickListener(v -> selectDate(selectedDate.plusDays(1)));
        dateCard.addView(previous, new LinearLayout.LayoutParams(dp(48), dp(48)));
        dateCard.addView(date, weighted());
        dateCard.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));
        addAt(body, dateCard, 0);
        if (!isToday) {
            Button goToday = button("Вернуться к сегодня", softAccent, accent, 14);
            goToday.setOnClickListener(v -> selectDate(LocalDate.now()));
            addAt(body, goToday, 8);
        }

        LinearLayout stats = row();
        stats.setGravity(Gravity.TOP);
        stats.addView(stat("Присутствуют", green, softGreen, true), weighted());
        LinearLayout.LayoutParams absentParams = weighted();
        absentParams.leftMargin = dp(10);
        stats.addView(stat("Отсутствуют", red, softRed, false), absentParams);
        addAt(body, stats, 12);
        refreshCounters();

        if (students.isEmpty()) {
            if (isToday) {
                emptyCard("Список пока пуст", "Добавь одногруппников, чтобы отмечать посещаемость.",
                        "Открыть список группы", () -> showTab(true));
            } else if (book.students(today).isEmpty()) {
                emptyCard("На эту дату список пуст", "Выбери другой день или настрой список группы.",
                        "Открыть список группы", () -> showTab(true));
            } else {
                emptyCard("На эту дату список пуст",
                        "Если нужно заполнить этот день задним числом, можно взять сегодняшний список.",
                        "Взять текущий список", () -> {
                            if (updateBook(updated -> updated.importPastRoster(selectedDate, today))) {
                                render();
                            }
                        });
            }
        } else {
            sectionTitle("Отметки");
            addAt(body, label("Сними галочку у тех, кто отсутствует.", 14, mutedColor, false), 4);
            LinearLayout list = cardList();
            for (int i = 0; i < students.size(); i++) {
                if (i != 0) divider(list);
                list.addView(attendanceRow(students.get(i)), new LinearLayout.LayoutParams(MATCH, WRAP));
            }
            addAt(body, list, 10);
        }
        copyBar(!students.isEmpty());
    }

    private Button arrow(String text, String description, boolean enabled) {
        Button button = button(text, enabled ? softAccent : surfaceColor,
                enabled ? accent : mutedColor, 12);
        button.setTextSize(28);
        button.setPadding(0, 0, 0, dp(2));
        button.setContentDescription(description);
        button.setEnabled(enabled);
        if (!enabled) button.setAlpha(0.5f);
        return button;
    }

    private LinearLayout stat(String name, int color, int background, boolean present) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(9), dp(14), dp(11));
        card.setBackground(shape(background, 16, 0));
        TextView caption = label(name, 13, color, true);
        caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(caption, new LinearLayout.LayoutParams(MATCH, WRAP));
        TextView count = label("", 27, color, true);
        count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(count, new LinearLayout.LayoutParams(MATCH, WRAP));
        if (present) presentCount = count;
        else absentCount = count;
        return card;
    }

    private void refreshCounters() {
        int absent = book.absent(selectedDate).size();
        int present = book.students(selectedDate).size() - absent;
        presentCount.setText(Integer.toString(present));
        presentCount.setContentDescription("Присутствуют: " + present);
        absentCount.setText(Integer.toString(absent));
        absentCount.setContentDescription("Отсутствуют: " + absent);
    }

    private LinearLayout attendanceRow(AttendanceBook.Student student) {
        LinearLayout line = row();
        line.setMinimumHeight(dp(58));
        line.setPadding(dp(15), dp(2), dp(5), dp(2));
        line.setBackground(touch(surfaceColor, 0, 0));
        line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        TextView name = label(student.name, 16, textColor, true);
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.addView(name, weighted());
        CheckBox check = new CheckBox(this);
        check.setContentDescription(student.name);
        check.setButtonTintList(new ColorStateList(
                new int[][] {{android.R.attr.state_checked}, {}}, new int[] {accent, mutedColor}));
        check.setChecked(book.present(selectedDate, student.id));
        if (Build.VERSION.SDK_INT >= 30) {
            check.setStateDescription(check.isChecked() ? "Присутствует" : "Отсутствует");
        }
        line.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));
        final LocalDate date = selectedDate;
        line.setOnClickListener(v -> check.performClick());
        check.setOnClickListener(v -> {
            if (updateBook(updated -> updated.setPresent(date, student.id, check.isChecked()))) {
                refreshCounters();
            } else {
                check.setChecked(book.present(date, student.id));
            }
            if (Build.VERSION.SDK_INT >= 30) {
                check.setStateDescription(check.isChecked() ? "Присутствует" : "Отсутствует");
            }
        });
        return line;
    }

    private void copyBar(boolean enabled) {
        LinearLayout bar = column();
        bar.setPadding(dp(20), dp(6), dp(20), dp(10));
        Button copy = button("Скопировать", enabled ? primary : lineColor,
                enabled ? onPrimary : mutedColor, 15);
        copy.setMinHeight(dp(52));
        copy.setTextSize(16);
        copy.setEnabled(enabled);
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            clipboard.setPrimaryClip(ClipData.newPlainText("Посещаемость", book.report(selectedDate)));
            if (Build.VERSION.SDK_INT < 33) {
                Toast.makeText(this, "Отчёт скопирован", Toast.LENGTH_SHORT).show();
            }
        });
        bar.addView(copy, new LinearLayout.LayoutParams(MATCH, WRAP));
        root.addView(bar, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private void renderRoster() {
        LocalDate today = LocalDate.now();
        List<AttendanceBook.Student> students = book.students(today);
        sectionTitle("Одногруппники");
        addAt(body, label("Изменения не меняют отчёты за прошлые дни.", 14, mutedColor, false), 4);
        Button add = button("Добавить человека", primary, onPrimary, 15);
        add.setOnClickListener(v -> editDialog(null, false));
        addAt(body, add, 14);
        Button importNames = button("Вставить список", softAccent, accent, 15);
        importNames.setOnClickListener(v -> editDialog(null, true));
        addAt(body, importNames, 8);

        if (students.isEmpty()) {
            emptyCard("Пока никого нет",
                    "Добавь человека вручную или вставь все фамилии разом.", null, null);
            return;
        }
        TextView total = label("Всего в группе: " + students.size(), 14, mutedColor, true);
        addAt(body, total, 16);
        LinearLayout list = cardList();
        for (int i = 0; i < students.size(); i++) {
            if (i != 0) divider(list);
            list.addView(rosterRow(students.get(i)), new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        addAt(body, list, 8);
    }

    private LinearLayout rosterRow(AttendanceBook.Student student) {
        LinearLayout line = column();
        line.setPadding(dp(15), dp(11), dp(10), dp(4));
        TextView name = label(student.name, 16, textColor, true);
        line.addView(name, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout actions = row();
        Button edit = button("Изменить", surfaceColor, accent, 10);
        edit.setContentDescription("Изменить: " + student.name);
        edit.setOnClickListener(v -> editDialog(student, false));
        Button remove = button("Удалить", surfaceColor, red, 10);
        remove.setContentDescription("Удалить: " + student.name);
        remove.setOnClickListener(v -> confirmRemove(student));
        actions.addView(edit, new LinearLayout.LayoutParams(WRAP, WRAP));
        actions.addView(remove, new LinearLayout.LayoutParams(WRAP, WRAP));
        line.addView(actions, new LinearLayout.LayoutParams(MATCH, WRAP));
        return line;
    }

    private void editDialog(AttendanceBook.Student student, boolean bulk) {
        EditText input = new EditText(this);
        input.setTextSize(16);
        input.setHint(bulk ? "Аксёнов И.И.\nАнейчик П.В." : "Аксёнов И.И.");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                | (bulk ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_TEXT_VARIATION_PERSON_NAME));
        if (bulk) {
            input.setMinLines(4);
            input.setMaxLines(8);
            input.setGravity(Gravity.TOP | Gravity.LEFT);
        } else {
            input.setSingleLine(true);
            input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        }
        if (student != null) {
            input.setText(student.name);
            input.setSelection(input.length());
        }
        LinearLayout content = column();
        content.setPadding(dp(22), dp(6), dp(22), 0);
        TextView help = label(bulk ? "По одному человеку на строку: фамилия и инициалы."
                : "Фамилия и инициалы", 14, mutedColor, false);
        addAt(content, help, 0);
        addAt(content, input, 5);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(bulk ? "Вставить список"
                        : student == null ? "Добавить человека" : "Изменить имя")
                .setView(content)
                .setNegativeButton("Отмена", null)
                .setPositiveButton(bulk || student == null ? "Добавить" : "Сохранить", null)
                .create();
        input.requestFocus();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        View submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        submit.setOnClickListener(v -> {
            LocalDate today = LocalDate.now();
            String text = input.getText().toString();
            try {
                boolean saved;
                if (bulk) {
                    List<String> names = new ArrayList<>();
                    for (String line : text.split("\\r\\n|[\\n\\r\\u0085\\u2028\\u2029]")) {
                        if (!line.matches("[\\p{Z}\\s]*")) names.add(line);
                    }
                    saved = updateBook(updated -> updated.add(today, names));
                } else if (student == null) {
                    saved = updateBook(updated -> updated.add(today, Collections.singletonList(text)));
                } else {
                    saved = updateBook(updated -> updated.rename(today, student.id, text));
                }
                if (saved) {
                    dialog.dismiss();
                    refreshRosterView();
                }
            } catch (IllegalArgumentException e) {
                input.setError(e.getMessage());
            }
        });
        if (!bulk) {
            input.setOnEditorActionListener((field, action, event) -> {
                if (action != EditorInfo.IME_ACTION_DONE) return false;
                return submit.performClick();
            });
        }
    }

    private void confirmRemove(AttendanceBook.Student student) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить из списка?")
                .setMessage("«" + student.name + "» исчезнет из списка за сегодня и следующие дни. "
                        + "Прошлые отметки сохранятся.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> {
                    if (updateBook(updated -> updated.remove(LocalDate.now(), student.id))) {
                        refreshRosterView();
                    }
                })
                .show();
    }

    private void refreshRosterView() {
        int position = scroll.getScrollY();
        render();
        ScrollView updated = scroll;
        updated.post(() -> updated.scrollTo(0, position));
    }

    private void sectionTitle(String name) {
        TextView title = label(name, 19, textColor, true);
        heading(title);
        addAt(body, title, 14);
    }

    private void emptyCard(String title, String description, String action, Runnable onClick) {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(shape(surfaceColor, 17, lineColor));
        card.addView(label(title, 17, textColor, true));
        addAt(card, label(description, 14, mutedColor, false), 6);
        if (action != null) {
            Button button = button(action, softAccent, accent, 12);
            button.setOnClickListener(v -> onClick.run());
            addAt(card, button, 12);
        }
        addAt(body, card, 14);
    }

    private LinearLayout cardList() {
        LinearLayout list = column();
        list.setPadding(dp(1), dp(1), dp(1), dp(1));
        list.setBackground(shape(surfaceColor, 16, lineColor));
        list.setClipToOutline(true);
        return list;
    }

    private void divider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(lineColor);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(MATCH, dp(1));
        layout.leftMargin = dp(14);
        layout.rightMargin = dp(14);
        parent.addView(divider, layout);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView label(String content, int size, int color, boolean strong) {
        TextView label = new TextView(this);
        label.setText(content);
        label.setTextSize(size);
        label.setTextColor(color);
        if (strong) label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return label;
    }

    private Button button(String text, int background, int color, int radius) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(color);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(12), dp(6), dp(12), dp(6));
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        button.setBackground(touch(background, radius, color));
        return button;
    }

    private RippleDrawable touch(int fill, int radius, int rippleColor) {
        int ink = (rippleColor == 0 ? textColor : rippleColor) & 0x00FFFFFF;
        return new RippleDrawable(ColorStateList.valueOf(ink | 0x24000000),
                shape(fill, radius, 0), shape(Color.WHITE, radius, 0));
    }

    private GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, WRAP, 1f);
    }

    private void addAt(LinearLayout parent, View child, int marginTop) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH, WRAP);
        params.topMargin = dp(marginTop);
        parent.addView(child, params);
    }

    private void heading(TextView label) {
        if (Build.VERSION.SDK_INT >= 28) label.setAccessibilityHeading(true);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
