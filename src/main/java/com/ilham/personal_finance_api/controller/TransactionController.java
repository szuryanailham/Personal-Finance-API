package com.ilham.personal_finance_api.controller;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.dto.BulkCreateTransactionRequest;
import com.ilham.personal_finance_api.dto.CreateTransactionRequest;
import com.ilham.personal_finance_api.dto.CreateTransactionResponse;
import com.ilham.personal_finance_api.dto.PaginationResponse;
import com.ilham.personal_finance_api.dto.TransactionExportFilter;
import com.ilham.personal_finance_api.dto.TransactionExportPeriod;
import com.ilham.personal_finance_api.dto.TransactionFilter;
import com.ilham.personal_finance_api.dto.TransactionResponse;
import com.ilham.personal_finance_api.dto.TransactionStatFilter;
import com.ilham.personal_finance_api.dto.TransactionStatPeriod;
import com.ilham.personal_finance_api.dto.TransactionStateResponse;
import com.ilham.personal_finance_api.dto.TransactionStatisticResponse;
import com.ilham.personal_finance_api.dto.TransactionType;
import com.ilham.personal_finance_api.dto.UpdateTransactionRequest;
import com.ilham.personal_finance_api.dto.WebResponse;
import com.ilham.personal_finance_api.dto.exporter.ExportFile;
import com.ilham.personal_finance_api.dto.importer.ImportResponse;
import com.ilham.personal_finance_api.services.ImportService;
import com.ilham.personal_finance_api.services.ImportTemplateService;
import com.ilham.personal_finance_api.services.TransactionExportService;
import org.springframework.web.multipart.MultipartFile;
import com.ilham.personal_finance_api.services.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController 
public class TransactionController {

    @Autowired 
    private TransactionService transactionService;

    @Autowired
    private ImportTemplateService importTemplateService;

    @Autowired
    private ImportService importService;

    @Autowired
    private TransactionExportService transactionExportService;

    private static final String IMPORT_TEMPLATE_FILENAME = "personal-finance-import-template.xlsx";
    private static final MediaType XLSX_MEDIA_TYPE =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @PostMapping (
        path = "/api/transaction",
        produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.APPLICATION_JSON_VALUE
    )

    public WebResponse<CreateTransactionResponse> create(User user , @Valid @RequestBody CreateTransactionRequest request) {
        CreateTransactionResponse response = transactionService.create(user, request);
        return new WebResponse<>(
            response,
            "Transaction created Successfully",
            null
            , null
        );
    }


    @PostMapping(
        path = "/api/transaction/bulk",
        produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public WebResponse<List<TransactionResponse>> bulkCreate(User user, @Valid @RequestBody BulkCreateTransactionRequest request) {
        List<TransactionResponse> transactions = transactionService.bulkCreate(user, request);
        return WebResponse.<List<TransactionResponse>>builder()
            .data(transactions)
            .message(transactions.size() + " transactions saved successfully")
            .build();
    }


    @DeleteMapping  (
        path = "/api/transaction/{transactionCode}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    
    public WebResponse<String> delete(User user, @PathVariable("transactionCode") String categoryId) {
        transactionService.delete(user, categoryId);
        return WebResponse.<String>builder()
        .message("Transaction deleted successfully")
        .build();
    }

   @GetMapping (
    path = "/api/transaction/{transactionCode}",
    produces = MediaType.APPLICATION_JSON_VALUE
)
    public WebResponse<TransactionResponse> get(User user , @PathVariable("transactionCode") String transactionCode) {
        TransactionResponse transactionResponse = transactionService.get(user, transactionCode);
        return WebResponse.<TransactionResponse>builder().data(transactionResponse).build();
    }

@GetMapping(
    path = "/api/transaction",
    produces = MediaType.APPLICATION_JSON_VALUE
)
public WebResponse<List<TransactionResponse>> getAll(
        User user,
        @RequestParam int skip,
        @RequestParam int limit,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) TransactionType type,
        @RequestParam(required = false) String sort
) {
    TransactionFilter filter = TransactionFilter.builder()
            .search(search)
            .date(date)
            .type(type)
            .sort(sort)
            .build();

    Page<TransactionResponse> transaction =
            transactionService.getAll(user, skip, limit, filter);

    return WebResponse.<List<TransactionResponse>>builder()
            .data(transaction.getContent())
            .paging(
                PaginationResponse.builder()
                    .currentPage(transaction.getNumber() + 1)
                    .totalPage(transaction.getTotalPages())
                    .size(transaction.getSize())
                    .totalItem(transaction.getTotalElements())
                    .build()
            )
            .build();
}


    @GetMapping(
        path = "/api/transaction/stat",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public WebResponse<TransactionStateResponse> getState(
            User user,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        TransactionStatFilter filter = TransactionStatFilter.builder()
            .type(TransactionStatPeriod.fromValue(type))
            .startDate(startDate)
            .endDate(endDate)
            .build();

        TransactionStateResponse state = transactionService.getStat(user, filter);
        return WebResponse.<TransactionStateResponse>builder().data(state).build();
    }


     @GetMapping( 
        path = "/api/transaction/statistic",
        produces = MediaType.APPLICATION_JSON_VALUE
    )

    public WebResponse<TransactionStatisticResponse> getStatistic(User user, @RequestParam(required = false) String periode) {
        TransactionStatisticResponse statistic =
            transactionService.getStatistic(user, periode);

        return WebResponse.<TransactionStatisticResponse>builder()
            .data(statistic)
            .message(null)
            .errors(null)
            .paging(null)
            .build();
    }

@PatchMapping (
    path = "/api/transaction/{transactionCode}",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE
)

public WebResponse<TransactionResponse>update(User user ,
    @Valid 
    @RequestBody 
    UpdateTransactionRequest request,
    @PathVariable("transactionCode") String transactionCode
) {
    TransactionResponse transactionResponse = transactionService.update(user, transactionCode, request);
    return WebResponse.<TransactionResponse>builder().data(transactionResponse).build();
}

    @GetMapping(path = "/api/transaction/import/template")
    public ResponseEntity<byte[]> downloadImportTemplate(User user) {
        byte[] file = importTemplateService.getTemplateImport(user);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + IMPORT_TEMPLATE_FILENAME + "\"")
            .contentType(XLSX_MEDIA_TYPE)
            .contentLength(file.length)
            .body(file);
    }

    @GetMapping(path = "/api/transaction/export")
    public ResponseEntity<byte[]> exportTransactions(
            User user,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        TransactionExportFilter filter = TransactionExportFilter.builder()
            .period(TransactionExportPeriod.fromValue(period))
            .startDate(startDate)
            .endDate(endDate)
            .build();

        ExportFile file = transactionExportService.export(user, filter);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
            .contentType(XLSX_MEDIA_TYPE)
            .contentLength(file.content().length)
            .body(file.content());
    }

    @PostMapping(
        path = "/api/transaction/import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ImportResponse importTransactions(User user, @RequestParam("file") MultipartFile file) {
        return importService.importFile(user, file);
    }
}
