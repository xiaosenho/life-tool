package com.lifetool.ledger;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lifetool.common.ApiResponse;
import com.lifetool.ledger.dto.CreateLedgerTransactionRequest;
import com.lifetool.ledger.dto.LedgerBudgetRequest;
import com.lifetool.ledger.dto.LedgerBudgetResponse;
import com.lifetool.ledger.dto.LedgerSummaryResponse;
import com.lifetool.ledger.dto.LedgerTransactionResponse;
import com.lifetool.ledger.dto.UpdateLedgerTransactionRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<LedgerTransactionResponse>>> listTransactions(
            @AuthenticationPrincipal String userId,
            @RequestParam String month) {
        return ResponseEntity.ok(ApiResponse.ok(ledgerService.listTransactions(userId, month)));
    }

    @PostMapping("/transactions")
    public ResponseEntity<ApiResponse<LedgerTransactionResponse>> createTransaction(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreateLedgerTransactionRequest request) {
        LedgerTransactionResponse response = ledgerService.createTransaction(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PatchMapping("/transactions/{id}")
    public ResponseEntity<ApiResponse<LedgerTransactionResponse>> updateTransaction(
            @AuthenticationPrincipal String userId,
            @PathVariable String id,
            @Valid @RequestBody UpdateLedgerTransactionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(ledgerService.updateTransaction(userId, id, request)));
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTransaction(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        ledgerService.deleteTransaction(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<LedgerSummaryResponse>> getSummary(
            @AuthenticationPrincipal String userId,
            @RequestParam String month) {
        return ResponseEntity.ok(ApiResponse.ok(ledgerService.getSummary(userId, month)));
    }

    @GetMapping("/budgets")
    public ResponseEntity<ApiResponse<List<LedgerBudgetResponse>>> listBudgets(
            @AuthenticationPrincipal String userId,
            @RequestParam String month) {
        return ResponseEntity.ok(ApiResponse.ok(ledgerService.listBudgets(userId, month)));
    }

    @PutMapping("/budgets/{month}")
    public ResponseEntity<ApiResponse<LedgerBudgetResponse>> saveBudget(
            @AuthenticationPrincipal String userId,
            @PathVariable String month,
            @Valid @RequestBody LedgerBudgetRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(ledgerService.saveBudget(userId, month, request)));
    }
}
