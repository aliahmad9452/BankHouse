package com.uniquemedia.bankhouse;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.uniquemedia.bankhouse.data.AppDatabase;
import com.uniquemedia.bankhouse.data.TransactionDao;
import com.uniquemedia.bankhouse.data.TransactionRecord;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_EXPORT_BACKUP = 1001;
    private static final int REQ_IMPORT_BACKUP = 1002;
    private static final int REQ_EXPORT_PDF = 1003;

    private static final String TYPE_INCOME = "Income";
    private static final String TYPE_EXPENSE = "Expense";
    private static final String TYPE_TRANSFER = "Transfer";
    private static final String ACCOUNT_CASH = "House Cash";
    private static final String ACCOUNT_BANK = "Bank";
    private static final String ACCOUNT_RECEIVABLE = "Receivable";

    private final String[] incomeSources = {"Wheat Sale", "Rice Sale", "Cotton Sale", "Milk Sale", "Arti Payment", "Brother Returned Money", "Other Income"};
    private final String[] expenseCategories = {"Grocery", "Electricity", "Gas", "Medicine", "School Fees", "Fertilizer", "Spray", "Seeds", "Labour", "Diesel", "Tractor Repair", "Other"};
    private final String[] storageAccounts = {ACCOUNT_CASH, ACCOUNT_BANK, ACCOUNT_RECEIVABLE};
    private final String[] paymentAccounts = {ACCOUNT_CASH, ACCOUNT_BANK};

    private TextView tvTotalWealth;
    private TextView tvHouseCash;
    private TextView tvBankBalance;
    private TextView tvReceivable;
    private TextView tvTotalIncome;
    private TextView tvTotalExpenses;
    private TextView tvFilterStatus;

    private TransactionDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final NumberFormat moneyFormat = NumberFormat.getNumberInstance(Locale.US);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);
    private final List<TransactionRecord> allTransactions = new ArrayList<>();
    private final List<TransactionRecord> visibleTransactions = new ArrayList<>();
    private TransactionAdapter adapter;
    private FilterState currentFilter = new FilterState();
    private File lastBackupFile;
    private File lastPdfFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        dao = AppDatabase.getInstance(this).transactionDao();
        bindViews();
        setupActions();
        setupList();
        loadTransactions();
    }

    private void bindViews() {
        tvTotalWealth = findViewById(R.id.tvTotalWealth);
        tvHouseCash = findViewById(R.id.tvHouseCash);
        tvBankBalance = findViewById(R.id.tvBankBalance);
        tvReceivable = findViewById(R.id.tvReceivable);
        tvTotalIncome = findViewById(R.id.tvTotalIncome);
        tvTotalExpenses = findViewById(R.id.tvTotalExpenses);
        tvFilterStatus = findViewById(R.id.tvFilterStatus);
    }

    private void setupActions() {
        findViewById(R.id.btnAddIncome).setOnClickListener(v -> showTransactionDialog(TYPE_INCOME, null));
        findViewById(R.id.btnAddExpense).setOnClickListener(v -> showTransactionDialog(TYPE_EXPENSE, null));
        findViewById(R.id.btnTransfer).setOnClickListener(v -> showTransactionDialog(TYPE_TRANSFER, null));
        findViewById(R.id.btnFilter).setOnClickListener(v -> showFilterDialog());
        findViewById(R.id.btnPdf).setOnClickListener(v -> showReportDialog(false));
        findViewById(R.id.btnExport).setOnClickListener(v -> exportBackupWithPicker());
        findViewById(R.id.btnImport).setOnClickListener(v -> importBackupWithPicker());
        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettingsDialog());
        findViewById(R.id.fabAdd).setOnClickListener(v -> showAddMenu());
    }

    private void setupList() {
        RecyclerView rvTransactions = findViewById(R.id.rvTransactions);
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter();
        rvTransactions.setAdapter(adapter);
    }

    private void loadTransactions() {
        executor.execute(() -> {
            List<TransactionRecord> records = dao.getAll();
            runOnUiThread(() -> {
                allTransactions.clear();
                allTransactions.addAll(records);
                applyFilter();
            });
        });
    }

    private void showAddMenu() {
        String[] options = {"Add Income", "Add Expense", "Transfer Money"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Add")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showTransactionDialog(TYPE_INCOME, null);
                    if (which == 1) showTransactionDialog(TYPE_EXPENSE, null);
                    if (which == 2) showTransactionDialog(TYPE_TRANSFER, null);
                })
                .show();
    }

    private void showTransactionDialog(String type, TransactionRecord editing) {
        TransactionForm form = createTransactionForm(type, editing);
        String title = editing == null ? "Add " + type : "Edit " + type;

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(form.container)
                .setPositiveButton("Save", (dialog, which) -> {
                    TransactionRecord record = editing == null ? new TransactionRecord() : editing;
                    if (!fillRecordFromForm(type, form, record)) {
                        return;
                    }
                    saveTransaction(record, editing == null);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private TransactionForm createTransactionForm(String type, TransactionRecord editing) {
        LinearLayout container = verticalContainer();
        EditText amount = input("Amount", InputType.TYPE_CLASS_NUMBER);
        EditText date = input("Date yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
        EditText notes = input("Notes", InputType.TYPE_CLASS_TEXT);
        Spinner first = null;
        Spinner second = null;

        date.setText(editing == null ? dateFormat.format(new Date()) : dateFormat.format(new Date(editing.dateMillis)));
        date.setFocusable(false);
        date.setOnClickListener(v -> pickDate(date));
        amount.setText(editing == null ? "" : String.valueOf(editing.amount));
        notes.setText(editing == null || editing.notes == null ? "" : editing.notes);

        if (TYPE_INCOME.equals(type)) {
            first = spinner(incomeSources, editing == null ? null : editing.source);
            second = spinner(storageAccounts, editing == null ? ACCOUNT_CASH : editing.accountTo);
            addLabelAndView(container, "Income Source", first);
            addLabelAndView(container, "Add To", second);
        } else if (TYPE_EXPENSE.equals(type)) {
            first = spinner(expenseCategories, editing == null ? null : editing.category);
            second = spinner(paymentAccounts, editing == null ? ACCOUNT_CASH : editing.accountFrom);
            addLabelAndView(container, "Category", first);
            addLabelAndView(container, "Paid From", second);
        } else {
            first = spinner(new String[]{"House Cash to Bank", "Bank to House Cash"}, editing != null && ACCOUNT_BANK.equals(editing.accountFrom) ? "Bank to House Cash" : "House Cash to Bank");
            addLabelAndView(container, "Direction", first);
        }

        addLabelAndView(container, "Amount", amount);
        addLabelAndView(container, "Date", date);
        addLabelAndView(container, "Notes", notes);
        return new TransactionForm(container, first, second, amount, date, notes);
    }

    private boolean fillRecordFromForm(String type, TransactionForm form, TransactionRecord record) {
        long amount = parseAmount(form.amount);
        long dateMillis = parseDate(form.date.getText().toString());
        if (amount <= 0 || dateMillis == 0) {
            Toast.makeText(this, "Enter valid amount and date", Toast.LENGTH_SHORT).show();
            return false;
        }

        record.type = type;
        record.amount = amount;
        record.dateMillis = dateMillis;
        record.notes = form.notes.getText().toString().trim();

        if (TYPE_INCOME.equals(type)) {
            record.source = form.first.getSelectedItem().toString();
            record.category = record.source;
            record.accountFrom = "";
            record.accountTo = form.second.getSelectedItem().toString();
        } else if (TYPE_EXPENSE.equals(type)) {
            record.category = form.first.getSelectedItem().toString();
            record.source = record.category;
            record.accountFrom = form.second.getSelectedItem().toString();
            record.accountTo = "";
        } else {
            String direction = form.first.getSelectedItem().toString();
            record.category = TYPE_TRANSFER;
            record.source = TYPE_TRANSFER;
            record.accountFrom = direction.startsWith("House") ? ACCOUNT_CASH : ACCOUNT_BANK;
            record.accountTo = direction.startsWith("House") ? ACCOUNT_BANK : ACCOUNT_CASH;
        }
        record.importFingerprint = fingerprint(record);
        return true;
    }

    private void saveTransaction(TransactionRecord record, boolean insert) {
        String message = transactionSavedMessage(record, insert);
        executor.execute(() -> {
            if (insert) {
                dao.insert(record);
            } else {
                dao.update(record);
            }
            loadTransactions();
            runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
        });
    }

    private String transactionSavedMessage(TransactionRecord record, boolean insert) {
        String action = insert ? "added" : "updated";
        String detail;
        if (TYPE_INCOME.equals(record.type)) {
            detail = safe(record.source) + " to " + safe(record.accountTo);
        } else if (TYPE_EXPENSE.equals(record.type)) {
            detail = safe(record.category) + " from " + safe(record.accountFrom);
        } else {
            detail = safe(record.accountFrom) + " to " + safe(record.accountTo);
        }
        return record.type + " " + action + ": " + currency(record.amount)
                + "\n" + detail
                + "\n" + displayDateFormat.format(new Date(record.dateMillis));
    }

    private void showDetails(TransactionRecord record) {
        String details = "Date: " + displayDateFormat.format(new Date(record.dateMillis))
                + "\nType: " + record.type
                + "\nCategory: " + safe(record.category)
                + "\nSource: " + safe(record.source)
                + "\nFrom: " + safe(record.accountFrom)
                + "\nTo: " + safe(record.accountTo)
                + "\nAmount: " + currency(record.amount)
                + "\nNotes: " + safe(record.notes);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Transaction Details")
                .setMessage(details)
                .setPositiveButton("Edit", (dialog, which) -> showTransactionDialog(record.type, record))
                .setNegativeButton("Delete", (dialog, which) -> confirmDelete(record))
                .setNeutralButton("Close", null)
                .show();
    }

    private void confirmDelete(TransactionRecord record) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Transaction")
                .setMessage("Delete this transaction and recalculate balances?")
                .setPositiveButton("Delete", (dialog, which) -> executor.execute(() -> {
                    dao.delete(record);
                    loadTransactions();
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFilterDialog() {
        LinearLayout container = verticalContainer();
        Spinner type = spinner(new String[]{"All", TYPE_INCOME, TYPE_EXPENSE, TYPE_TRANSFER}, currentFilter.type);
        EditText category = input("Category or source", InputType.TYPE_CLASS_TEXT);
        EditText account = input("Payment source/account", InputType.TYPE_CLASS_TEXT);
        EditText minAmount = input("Minimum amount", InputType.TYPE_CLASS_NUMBER);
        EditText maxAmount = input("Maximum amount", InputType.TYPE_CLASS_NUMBER);
        EditText fromDate = input("From date yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
        EditText toDate = input("To date yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);

        category.setText(currentFilter.category);
        account.setText(currentFilter.account);
        minAmount.setText(currentFilter.minAmount > 0 ? String.valueOf(currentFilter.minAmount) : "");
        maxAmount.setText(currentFilter.maxAmount > 0 ? String.valueOf(currentFilter.maxAmount) : "");
        fromDate.setText(currentFilter.fromMillis > 0 ? dateFormat.format(new Date(currentFilter.fromMillis)) : "");
        toDate.setText(currentFilter.toMillis > 0 ? dateFormat.format(new Date(currentFilter.toMillis)) : "");
        fromDate.setFocusable(false);
        toDate.setFocusable(false);
        fromDate.setOnClickListener(v -> pickDate(fromDate));
        toDate.setOnClickListener(v -> pickDate(toDate));

        addLabelAndView(container, "Type", type);
        addLabelAndView(container, "Category", category);
        addLabelAndView(container, "Payment Source", account);
        addLabelAndView(container, "Amount Range", minAmount);
        container.addView(maxAmount);
        addLabelAndView(container, "Date Range", fromDate);
        container.addView(toDate);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Search & Filters")
                .setView(container)
                .setPositiveButton("Apply", (dialog, which) -> {
                    currentFilter.type = type.getSelectedItem().toString();
                    currentFilter.category = category.getText().toString().trim();
                    currentFilter.account = account.getText().toString().trim();
                    currentFilter.minAmount = parseOptionalAmount(minAmount);
                    currentFilter.maxAmount = parseOptionalAmount(maxAmount);
                    currentFilter.fromMillis = parseOptionalDate(fromDate);
                    currentFilter.toMillis = endOfDay(parseOptionalDate(toDate));
                    applyFilter();
                })
                .setNegativeButton("Clear", (dialog, which) -> {
                    currentFilter = new FilterState();
                    applyFilter();
                })
                .show();
    }

    private void applyFilter() {
        visibleTransactions.clear();
        for (TransactionRecord record : allTransactions) {
            if (currentFilter.matches(record)) {
                visibleTransactions.add(record);
            }
        }
        adapter.notifyDataSetChanged();
        refreshDashboard();
        tvFilterStatus.setText(currentFilter.isEmpty()
                ? "Showing all transactions"
                : "Showing " + visibleTransactions.size() + " filtered transactions");
    }

    private void refreshDashboard() {
        long cash = 0;
        long bank = 0;
        long receivable = 0;
        long income = 0;
        long expenses = 0;

        for (TransactionRecord record : allTransactions) {
            if (TYPE_INCOME.equals(record.type)) {
                income += record.amount;
                if (ACCOUNT_CASH.equals(record.accountTo)) cash += record.amount;
                if (ACCOUNT_BANK.equals(record.accountTo)) bank += record.amount;
                if (ACCOUNT_RECEIVABLE.equals(record.accountTo)) receivable += record.amount;
            } else if (TYPE_EXPENSE.equals(record.type)) {
                expenses += record.amount;
                if (ACCOUNT_CASH.equals(record.accountFrom)) cash -= record.amount;
                if (ACCOUNT_BANK.equals(record.accountFrom)) bank -= record.amount;
            } else if (TYPE_TRANSFER.equals(record.type)) {
                if (ACCOUNT_CASH.equals(record.accountFrom)) cash -= record.amount;
                if (ACCOUNT_BANK.equals(record.accountFrom)) bank -= record.amount;
                if (ACCOUNT_CASH.equals(record.accountTo)) cash += record.amount;
                if (ACCOUNT_BANK.equals(record.accountTo)) bank += record.amount;
            }
        }

        tvTotalWealth.setText(currency(cash + bank + receivable));
        tvHouseCash.setText("House Cash\n" + currency(cash));
        tvBankBalance.setText("Bank\n" + currency(bank));
        tvReceivable.setText("Receivable\n" + currency(receivable));
        tvTotalIncome.setText("Income\n" + currency(income));
        tvTotalExpenses.setText("Expenses\n" + currency(expenses));
    }

    private void showReportDialog(boolean shareAfterCreate) {
        String[] ranges = {"Daily", "Weekly", "Monthly", "Yearly", "Custom Date Range", "All"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("PDF Report")
                .setItems(ranges, (dialog, which) -> {
                    if (which == 4) {
                        showCustomReportDialog(shareAfterCreate);
                    } else {
                        long[] range = reportRange(ranges[which]);
                        createPdf(range[0], range[1], shareAfterCreate, !shareAfterCreate);
                    }
                })
                .show();
    }

    private void showCustomReportDialog(boolean shareAfterCreate) {
        LinearLayout container = verticalContainer();
        EditText from = input("From date yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
        EditText to = input("To date yyyy-MM-dd", InputType.TYPE_CLASS_DATETIME);
        from.setFocusable(false);
        to.setFocusable(false);
        from.setOnClickListener(v -> pickDate(from));
        to.setOnClickListener(v -> pickDate(to));
        addLabelAndView(container, "From", from);
        addLabelAndView(container, "To", to);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Custom Report")
                .setView(container)
                .setPositiveButton("Create", (dialog, which) -> createPdf(parseOptionalDate(from), endOfDay(parseOptionalDate(to)), shareAfterCreate, !shareAfterCreate))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createPdf(long fromMillis, long toMillis, boolean shareAfterCreate, boolean exportWithPicker) {
        executor.execute(() -> {
            try {
                File file = new File(getExternalFilesDir("Documents"), pdfName());
                writePdf(file, filteredByDate(fromMillis, toMillis));
                lastPdfFile = file;
                runOnUiThread(() -> {
                    String message = exportWithPicker
                            ? "PDF ready. Choose where to save it."
                            : "PDF created: " + file.getName();
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    if (shareAfterCreate) shareFile(file, "application/pdf");
                    if (exportWithPicker) exportPdfWithPicker(file);
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "PDF failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void writePdf(File file, List<TransactionRecord> records) throws IOException {
        PdfDocument pdf = new PdfDocument();
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        int pageNumber = 1;
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create());
        Canvas canvas = page.getCanvas();
        Summary summary = summarize(records);
        int y = drawStatementHeader(canvas, paint, summary, records.size());
        y = drawTableHeader(canvas, paint, y);

        for (TransactionRecord record : records) {
            int rowHeight = statementRowHeight(paint, record);
            if (y + rowHeight > 780) {
                drawStatementFooter(canvas, paint, pageNumber);
                pdf.finishPage(page);
                pageNumber++;
                page = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create());
                canvas = page.getCanvas();
                y = drawContinuationHeader(canvas, paint);
                y = drawTableHeader(canvas, paint, y);
            }
            y = drawStatementRow(canvas, paint, record, y, rowHeight);
        }

        if (records.isEmpty()) {
            paint.setColor(0xFF68756D);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(11);
            canvas.drawText("No transactions found for this statement period.", 42, y + 24, paint);
        }

        drawStatementFooter(canvas, paint, pageNumber);
        pdf.finishPage(page);
        try (FileOutputStream output = new FileOutputStream(file)) {
            pdf.writeTo(output);
        }
        pdf.close();
    }

    private int drawStatementHeader(Canvas canvas, Paint paint, Summary summary, int transactionCount) {
        canvas.drawColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0E4F2B);
        canvas.drawRoundRect(28, 24, 567, 118, 16, 16, paint);

        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(24);
        canvas.drawText("BANK HOUSE", 44, 60, paint);
        paint.setTextSize(10);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText("Professional Financial Statement", 46, 80, paint);
        canvas.drawText("Generated: " + dateFormat.format(new Date()), 46, 99, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(12);
        canvas.drawText("Statement Balance", 548, 58, paint);
        paint.setTextSize(18);
        canvas.drawText(currency(summary.totalWealth), 548, 84, paint);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(9);
        canvas.drawText(transactionCount + " transactions", 548, 101, paint);
        paint.setTextAlign(Paint.Align.LEFT);

        int y = 142;
        drawSummaryBox(canvas, paint, 34, y, 162, "House Cash", currency(summary.cash), 0xFFEAF7EF, 0xFF176D3B);
        drawSummaryBox(canvas, paint, 174, y, 302, "Bank Balance", currency(summary.bank), 0xFFEFF4FF, 0xFF276EF1);
        drawSummaryBox(canvas, paint, 314, y, 442, "Receivable", currency(summary.receivable), 0xFFFFF4DE, 0xFFB87412);
        drawSummaryBox(canvas, paint, 454, y, 561, "Income / Expense", currency(summary.income) + " / " + currency(summary.expenses), 0xFFF7F8FA, 0xFF17211B);
        return 228;
    }

    private void drawSummaryBox(Canvas canvas, Paint paint, int left, int top, int right, String label, String value, int bgColor, int valueColor) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bgColor);
        canvas.drawRoundRect(left, top, right, top + 58, 12, 12, paint);
        paint.setColor(0xFF68756D);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(9);
        canvas.drawText(label, left + 10, top + 20, paint);
        paint.setColor(valueColor);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(11);
        canvas.drawText(shortText(value, 20), left + 10, top + 42, paint);
    }

    private int drawContinuationHeader(Canvas canvas, Paint paint) {
        canvas.drawColor(Color.WHITE);
        paint.setColor(0xFF0E4F2B);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(18);
        canvas.drawText("BANK HOUSE", 34, 42, paint);
        paint.setColor(0xFF68756D);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(10);
        canvas.drawText("Statement continued", 34, 59, paint);
        return 78;
    }

    private int drawTableHeader(Canvas canvas, Paint paint, int y) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF17211B);
        canvas.drawRoundRect(34, y, 561, y + 28, 8, 8, paint);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(9);
        canvas.drawText("DATE", 42, y + 18, paint);
        canvas.drawText("TYPE", 104, y + 18, paint);
        canvas.drawText("DETAILS", 160, y + 18, paint);
        canvas.drawText("ACCOUNT", 278, y + 18, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("AMOUNT", 432, y + 18, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("NOTES", 446, y + 18, paint);
        return y + 32;
    }

    private int drawStatementRow(Canvas canvas, Paint paint, TransactionRecord record, int y, int rowHeight) {
        boolean expense = TYPE_EXPENSE.equals(record.type);
        boolean transfer = TYPE_TRANSFER.equals(record.type);
        int accent = expense ? 0xFFC7362F : transfer ? 0xFF276EF1 : 0xFF17884B;
        int bg = expense ? 0xFFFFF4F3 : transfer ? 0xFFF2F6FF : 0xFFF2FBF5;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bg);
        canvas.drawRoundRect(34, y, 561, y + rowHeight - 4, 8, 8, paint);
        paint.setColor(accent);
        canvas.drawRoundRect(34, y, 38, y + rowHeight - 4, 4, 4, paint);

        int textY = y + 17;
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(8.5f);
        paint.setColor(0xFF17211B);
        canvas.drawText(displayDateFormat.format(new Date(record.dateMillis)), 42, textY, paint);

        paint.setColor(accent);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(record.type, 104, textY, paint);

        paint.setTypeface(Typeface.DEFAULT);
        paint.setColor(0xFF17211B);
        drawWrappedText(canvas, paint, statementDetails(record), 160, textY, 108, 11, 3);
        drawWrappedText(canvas, paint, statementAccount(record), 278, textY, 78, 11, 3);

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setColor(accent);
        canvas.drawText((expense ? "-" : "") + currency(record.amount), 432, textY, paint);
        paint.setTextAlign(Paint.Align.LEFT);

        paint.setTypeface(Typeface.DEFAULT);
        paint.setColor(0xFF37423B);
        drawWrappedText(canvas, paint, safe(record.notes).isEmpty() ? "-" : safe(record.notes), 446, textY, 106, 11, 4);
        return y + rowHeight;
    }

    private int statementRowHeight(Paint paint, TransactionRecord record) {
        paint.setTextSize(8.5f);
        int detailsLines = wrappedLineCount(paint, statementDetails(record), 108, 3);
        int accountLines = wrappedLineCount(paint, statementAccount(record), 78, 3);
        int noteLines = wrappedLineCount(paint, safe(record.notes).isEmpty() ? "-" : safe(record.notes), 106, 4);
        int lines = Math.max(1, Math.max(noteLines, Math.max(detailsLines, accountLines)));
        return Math.max(34, 18 + (lines * 11));
    }

    private String statementDetails(TransactionRecord record) {
        if (TYPE_INCOME.equals(record.type)) return safe(record.source);
        if (TYPE_EXPENSE.equals(record.type)) return safe(record.category);
        return "Money Transfer";
    }

    private String statementAccount(TransactionRecord record) {
        if (TYPE_INCOME.equals(record.type)) return "To: " + safe(record.accountTo);
        if (TYPE_EXPENSE.equals(record.type)) return "From: " + safe(record.accountFrom);
        return safe(record.accountFrom) + " to " + safe(record.accountTo);
    }

    private void drawStatementFooter(Canvas canvas, Paint paint, int pageNumber) {
        paint.setColor(0xFFE1E9E4);
        canvas.drawLine(34, 804, 561, 804, paint);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(9);
        paint.setColor(0xFF68756D);
        canvas.drawText("Developed by Ali Ahmad", 34, 822, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Page " + pageNumber, 561, 822, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawWrappedText(Canvas canvas, Paint paint, String text, int x, int y, int maxWidth, int lineHeight, int maxLines) {
        List<String> lines = wrapText(paint, text, maxWidth, maxLines);
        for (int i = 0; i < lines.size(); i++) {
            canvas.drawText(lines.get(i), x, y + (i * lineHeight), paint);
        }
    }

    private int wrappedLineCount(Paint paint, String text, int maxWidth, int maxLines) {
        return wrapText(paint, text, maxWidth, maxLines).size();
    }

    private List<String> wrapText(Paint paint, String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        String cleanText = safe(text).trim();
        if (cleanText.isEmpty()) cleanText = "-";
        String[] words = cleanText.split("\\s+");
        String line = "";
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (paint.measureText(candidate) <= maxWidth) {
                line = candidate;
            } else {
                if (!line.isEmpty()) lines.add(line);
                line = word;
                if (lines.size() == maxLines - 1) break;
            }
        }
        if (!line.isEmpty() && lines.size() < maxLines) lines.add(line);
        if (lines.isEmpty()) lines.add("-");
        return lines;
    }

    private void exportBackupWithPicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "BankHouse_Backup_" + dateFormat.format(new Date()).replace("-", "_") + ".bankhouse");
        startActivityForResult(intent, REQ_EXPORT_BACKUP);
    }

    private void importBackupWithPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT_BACKUP);
    }

    private void exportPdfWithPicker(File source) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, source.getName());
        startActivityForResult(intent, REQ_EXPORT_PDF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT_BACKUP) writeBackupToUri(uri);
        if (requestCode == REQ_IMPORT_BACKUP) readBackupFromUri(uri);
        if (requestCode == REQ_EXPORT_PDF && lastPdfFile != null) copyFileToUri(lastPdfFile, uri, "PDF exported");
    }

    private void writeBackupToUri(Uri uri) {
        executor.execute(() -> {
            try {
                String json = backupJson(allTransactions);
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    output.write(json.getBytes());
                }
                lastBackupFile = cacheFile("BankHouse_Backup.bankhouse", json);
                runOnUiThread(() -> Toast.makeText(this, "Backup exported", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void readBackupFromUri(Uri uri) {
        executor.execute(() -> {
            try {
                StringBuilder builder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                }
                List<TransactionRecord> imported = parseBackup(builder.toString());
                int added = 0;
                for (TransactionRecord record : imported) {
                    if (dao.countByFingerprint(record.importFingerprint) == 0) {
                        dao.insert(record);
                        added++;
                    }
                }
                int finalAdded = added;
                runOnUiThread(() -> Toast.makeText(this, "Restore complete. Added " + finalAdded + " records", Toast.LENGTH_LONG).show());
                loadTransactions();
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Invalid backup: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private String backupJson(List<TransactionRecord> records) throws JSONException {
        Summary summary = summarize(records);
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("exportDate", dateFormat.format(new Date()));
        root.put("houseCash", summary.cash);
        root.put("bankBalance", summary.bank);
        root.put("receivable", summary.receivable);
        JSONArray array = new JSONArray();
        for (TransactionRecord record : records) {
            JSONObject item = new JSONObject();
            item.put("type", record.type);
            item.put("category", safe(record.category));
            item.put("source", safe(record.source));
            item.put("accountFrom", safe(record.accountFrom));
            item.put("accountTo", safe(record.accountTo));
            item.put("amount", record.amount);
            item.put("dateMillis", record.dateMillis);
            item.put("notes", safe(record.notes));
            item.put("fingerprint", record.importFingerprint);
            array.put(item);
        }
        root.put("transactions", array);
        return root.toString(2);
    }

    private List<TransactionRecord> parseBackup(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optInt("version", -1) != 1 || !root.has("transactions")) {
            throw new JSONException("Unsupported .bankhouse file");
        }
        JSONArray array = root.getJSONArray("transactions");
        List<TransactionRecord> records = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            TransactionRecord record = new TransactionRecord();
            record.type = item.getString("type");
            record.category = item.optString("category");
            record.source = item.optString("source");
            record.accountFrom = item.optString("accountFrom");
            record.accountTo = item.optString("accountTo");
            record.amount = item.getLong("amount");
            record.dateMillis = item.getLong("dateMillis");
            record.notes = item.optString("notes");
            record.importFingerprint = item.optLong("fingerprint", fingerprint(record));
            if (record.amount <= 0 || record.dateMillis <= 0 || !isKnownType(record.type)) {
                throw new JSONException("Bad transaction at row " + (i + 1));
            }
            records.add(record);
        }
        return records;
    }

    private void showSettingsDialog() {
        String[] options = {"Export Backup", "Import Backup", "Generate PDF Report", "Share PDF", "Share Backup", "Delete All Data", "Dark Mode", "About App"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Settings")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) exportBackupWithPicker();
                    if (which == 1) importBackupWithPicker();
                    if (which == 2) showReportDialog(false);
                    if (which == 3) showReportDialog(true);
                    if (which == 4) shareBackup();
                    if (which == 5) confirmDeleteAll();
                    if (which == 6) toggleDarkMode();
                    if (which == 7) showAbout();
                })
                .show();
    }

    private void shareBackup() {
        executor.execute(() -> {
            try {
                lastBackupFile = cacheFile("BankHouse_Backup.bankhouse", backupJson(allTransactions));
                runOnUiThread(() -> shareFile(lastBackupFile, "application/octet-stream"));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Share backup failed", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void confirmDeleteAll() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete All Data")
                .setMessage("This will permanently delete every transaction and reset balances.")
                .setPositiveButton("Delete All", (dialog, which) -> executor.execute(() -> {
                    dao.deleteAll();
                    loadTransactions();
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleDarkMode() {
        int current = AppCompatDelegate.getDefaultNightMode();
        AppCompatDelegate.setDefaultNightMode(current == AppCompatDelegate.MODE_NIGHT_YES
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_YES);
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("About BANK HOUSE")
                .setMessage("Offline finance manager for farming families.\n\nData is stored locally with Room. No Firebase. No internet required.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void shareFile(File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share with"));
    }

    private void copyFileToUri(File source, Uri uri, String message) {
        executor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri);
                 java.io.FileInputStream input = new java.io.FileInputStream(source)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private File cacheFile(String name, String content) throws IOException {
        File file = new File(getCacheDir(), name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content.getBytes());
        }
        return file;
    }

    private long[] reportRange(String range) {
        Calendar cal = Calendar.getInstance();
        long to = endOfDay(cal.getTimeInMillis());
        if ("All".equals(range)) return new long[]{0, 0};
        if ("Yearly".equals(range)) cal.set(Calendar.DAY_OF_YEAR, 1);
        if ("Monthly".equals(range)) cal.set(Calendar.DAY_OF_MONTH, 1);
        if ("Weekly".equals(range)) cal.add(Calendar.DAY_OF_YEAR, -6);
        startOfDay(cal);
        return new long[]{cal.getTimeInMillis(), to};
    }

    private List<TransactionRecord> filteredByDate(long fromMillis, long toMillis) {
        List<TransactionRecord> records = new ArrayList<>();
        for (TransactionRecord record : allTransactions) {
            if ((fromMillis == 0 || record.dateMillis >= fromMillis) && (toMillis == 0 || record.dateMillis <= toMillis)) {
                records.add(record);
            }
        }
        return records;
    }

    private Summary summarize(List<TransactionRecord> records) {
        Summary summary = new Summary();
        for (TransactionRecord record : records) {
            if (TYPE_INCOME.equals(record.type)) {
                summary.income += record.amount;
                if (ACCOUNT_CASH.equals(record.accountTo)) summary.cash += record.amount;
                if (ACCOUNT_BANK.equals(record.accountTo)) summary.bank += record.amount;
                if (ACCOUNT_RECEIVABLE.equals(record.accountTo)) summary.receivable += record.amount;
            } else if (TYPE_EXPENSE.equals(record.type)) {
                summary.expenses += record.amount;
                if (ACCOUNT_CASH.equals(record.accountFrom)) summary.cash -= record.amount;
                if (ACCOUNT_BANK.equals(record.accountFrom)) summary.bank -= record.amount;
            } else if (TYPE_TRANSFER.equals(record.type)) {
                if (ACCOUNT_CASH.equals(record.accountFrom)) summary.cash -= record.amount;
                if (ACCOUNT_BANK.equals(record.accountFrom)) summary.bank -= record.amount;
                if (ACCOUNT_CASH.equals(record.accountTo)) summary.cash += record.amount;
                if (ACCOUNT_BANK.equals(record.accountTo)) summary.bank += record.amount;
            }
        }
        summary.totalWealth = summary.cash + summary.bank + summary.receivable;
        return summary;
    }

    private void pickDate(EditText target) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            cal.set(year, month, dayOfMonth);
            target.setText(dateFormat.format(cal.getTime()));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private LinearLayout verticalContainer() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(22);
        container.setPadding(padding, dp(10), padding, dp(4));
        return container;
    }

    private EditText input(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(inputType);
        editText.setTextColor(0xFF17211B);
        editText.setHintTextColor(0xFF8A968F);
        editText.setTextSize(15);
        editText.setMinHeight(dp(50));
        editText.setPadding(dp(14), 0, dp(14), 0);
        editText.setBackgroundResource(R.drawable.bg_dialog_field);
        editText.setSingleLine(!"Notes".equals(hint));
        if ("Notes".equals(hint)) {
            editText.setMinLines(2);
            editText.setGravity(Gravity.TOP | Gravity.START);
            editText.setPadding(dp(14), dp(12), dp(14), dp(12));
        }
        editText.setLayoutParams(dialogFieldParams());
        return editText;
    }

    private Spinner spinner(String[] options, String selected) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setMinimumHeight(dp(50));
        spinner.setPadding(dp(10), 0, dp(10), 0);
        spinner.setBackgroundResource(R.drawable.bg_dialog_field);
        spinner.setLayoutParams(dialogFieldParams());
        if (selected != null) {
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(selected)) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }
        return spinner;
    }

    private void addLabelAndView(LinearLayout container, String labelText, android.view.View view) {
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(0xFF17211B);
        label.setTextSize(13);
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(0, dp(12), 0, 0);
        container.addView(label);
        container.addView(view);
    }

    private LinearLayout.LayoutParams dialogFieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        return params;
    }

    private long parseAmount(EditText input) {
        try {
            return Long.parseLong(input.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long parseOptionalAmount(EditText input) {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) return 0;
        return parseAmount(input);
    }

    private long parseDate(String value) {
        try {
            Date date = dateFormat.parse(value.trim());
            return date == null ? 0 : date.getTime();
        } catch (ParseException ignored) {
            return 0;
        }
    }

    private long parseOptionalDate(EditText input) {
        String value = input.getText().toString().trim();
        return value.isEmpty() ? 0 : parseDate(value);
    }

    private void startOfDay(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private long endOfDay(long millis) {
        if (millis == 0) return 0;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }

    private String currency(long amount) {
        return "Rs. " + moneyFormat.format(amount);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String shortText(String value, int max) {
        value = safe(value);
        return value.length() <= max ? value : value.substring(0, max - 1);
    }

    private boolean isKnownType(String type) {
        return TYPE_INCOME.equals(type) || TYPE_EXPENSE.equals(type) || TYPE_TRANSFER.equals(type);
    }

    private long fingerprint(TransactionRecord record) {
        String raw = record.type + "|" + record.category + "|" + record.source + "|" + record.accountFrom + "|"
                + record.accountTo + "|" + record.amount + "|" + record.dateMillis + "|" + record.notes;
        return raw.hashCode();
    }

    private String pdfName() {
        return "BankHouse_Report_" + dateFormat.format(new Date()).replace("-", "_") + ".pdf";
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private static class TransactionForm {
        final LinearLayout container;
        final Spinner first;
        final Spinner second;
        final EditText amount;
        final EditText date;
        final EditText notes;

        TransactionForm(LinearLayout container, Spinner first, Spinner second, EditText amount, EditText date, EditText notes) {
            this.container = container;
            this.first = first;
            this.second = second;
            this.amount = amount;
            this.date = date;
            this.notes = notes;
        }
    }

    private static class FilterState {
        String type = "All";
        String category = "";
        String account = "";
        long minAmount;
        long maxAmount;
        long fromMillis;
        long toMillis;

        boolean matches(TransactionRecord record) {
            if (!"All".equals(type) && !type.equals(record.type)) return false;
            if (!category.isEmpty() && !(contains(record.category, category) || contains(record.source, category))) return false;
            if (!account.isEmpty() && !(contains(record.accountFrom, account) || contains(record.accountTo, account))) return false;
            if (minAmount > 0 && record.amount < minAmount) return false;
            if (maxAmount > 0 && record.amount > maxAmount) return false;
            if (fromMillis > 0 && record.dateMillis < fromMillis) return false;
            return toMillis == 0 || record.dateMillis <= toMillis;
        }

        boolean isEmpty() {
            return "All".equals(type) && category.isEmpty() && account.isEmpty()
                    && minAmount == 0 && maxAmount == 0 && fromMillis == 0 && toMillis == 0;
        }

        private boolean contains(String value, String query) {
            return value != null && value.toLowerCase(Locale.US).contains(query.toLowerCase(Locale.US));
        }
    }

    private static class Summary {
        long cash;
        long bank;
        long receivable;
        long income;
        long expenses;
        long totalWealth;
    }

    private class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {
        @NonNull
        @Override
        public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackgroundResource(R.drawable.bg_transaction_row);
            row.setElevation(dp(1));

            TextView title = new TextView(parent.getContext());
            title.setTextColor(0xFF17211B);
            title.setTextSize(16);
            title.setTypeface(null, Typeface.BOLD);
            TextView subtitle = new TextView(parent.getContext());
            subtitle.setTextSize(13);
            subtitle.setTextColor(0xFF68756D);
            TextView amount = new TextView(parent.getContext());
            amount.setGravity(Gravity.END);
            amount.setTextSize(16);
            amount.setTypeface(null, Typeface.BOLD);

            row.addView(title);
            row.addView(subtitle);
            row.addView(amount);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(params);
            return new TransactionViewHolder(row, title, subtitle, amount);
        }

        @Override
        public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
            TransactionRecord record = visibleTransactions.get(position);
            holder.title.setText(record.type + " - " + safe(record.category));
            holder.subtitle.setText(displayDateFormat.format(new Date(record.dateMillis)) + "  " + safe(record.accountFrom) + " -> " + safe(record.accountTo) + "\n" + safe(record.notes));
            holder.amount.setText((TYPE_EXPENSE.equals(record.type) ? "-" : "") + currency(record.amount));
            holder.amount.setTextColor(TYPE_EXPENSE.equals(record.type) ? 0xFFC62828 : 0xFF2E7D32);
            holder.itemView.setOnClickListener(v -> showDetails(record));
        }

        @Override
        public int getItemCount() {
            return visibleTransactions.size();
        }

        class TransactionViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView subtitle;
            final TextView amount;

            TransactionViewHolder(@NonNull LinearLayout itemView, TextView title, TextView subtitle, TextView amount) {
                super(itemView);
                this.title = title;
                this.subtitle = subtitle;
                this.amount = amount;
            }
        }
    }
}
