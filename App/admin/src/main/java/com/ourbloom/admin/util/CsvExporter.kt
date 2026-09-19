package com.ourbloom.admin.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ourbloom.admin.data.models.SavingsTransaction
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val fileTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun exportTransactions(
        context: Context,
        transactions: List<SavingsTransaction>,
        filePrefix: String = "OurBloom_Financial_Ledger"
    ) = exportTransactionsToCsv(context, transactions, filePrefix)

    fun exportTransactionsToCsv(
        context: Context,
        transactions: List<SavingsTransaction>,
        filePrefix: String = "OurBloom_Financial_Ledger"
    ) {
        try {
            val fileName = "${filePrefix}_${fileTimestamp.format(Date())}.csv"
            val file = File(context.cacheDir, fileName)

            FileWriter(file).use { writer ->
                // Header
                writer.append("Date,Transaction ID,Couple ID,User,Type,Amount (INR),Platform Fee (INR),Net Vault Credit (INR),Payment Method,Settlement Status,Bank UTR / Ref,Category,Note\n")

                for (tx in transactions) {
                    val dateStr = if (tx.timestamp > 0) dateFormat.format(Date(tx.timestamp)) else "N/A"
                    val txId = cleanCsv(tx.id.ifBlank { tx.platformTransactionId })
                    val coupleId = cleanCsv(tx.coupleId)
                    val user = cleanCsv(tx.userName.ifBlank { tx.userId })
                    val type = cleanCsv(tx.type.uppercase(Locale.US))

                    val grossRupees = if (tx.payableAmountPaise > 0) {
                        tx.payableAmountPaise / 100.0
                    } else if (tx.grossAmountPaise > 0) {
                        tx.grossAmountPaise / 100.0
                    } else {
                        tx.amount
                    }

                    val feeRupees = if (tx.platformFeePaise > 0) tx.platformFeePaise / 100.0 else tx.platformFee
                    val netRupees = if (tx.vaultAmountPaise > 0) {
                        tx.vaultAmountPaise / 100.0
                    } else if (tx.netVaultCreditPaise > 0) {
                        tx.netVaultCreditPaise / 100.0
                    } else {
                        tx.amount
                    }

                    val method = cleanCsv(tx.paymentMethod)
                    val status = cleanCsv(tx.settlementStatus)
                    val bankRef = cleanCsv(tx.merchantUtr ?: tx.providerBankReference ?: tx.utrNumber)
                    val category = cleanCsv(tx.category)
                    val note = cleanCsv(tx.note)

                    writer.append("\"$dateStr\",")
                    writer.append("\"$txId\",")
                    writer.append("\"$coupleId\",")
                    writer.append("\"$user\",")
                    writer.append("\"$type\",")
                    writer.append(String.format(Locale.US, "%.2f,", grossRupees))
                    writer.append(String.format(Locale.US, "%.2f,", feeRupees))
                    writer.append(String.format(Locale.US, "%.2f,", netRupees))
                    writer.append("\"$method\",")
                    writer.append("\"$status\",")
                    writer.append("\"$bankRef\",")
                    writer.append("\"$category\",")
                    writer.append("\"$note\"\n")
                }
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$filePrefix Export")
                putExtra(Intent.EXTRA_TEXT, "Exported ${transactions.size} transactions from Our Bloom Financial Ledger.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Financial Statement CSV")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to export CSV: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportRevenueToCsv(
        context: Context,
        revenueRecords: List<com.ourbloom.admin.data.models.RevenueRecord>,
        filePrefix: String = "OurBloom_Canonical_Revenue"
    ) {
        try {
            val fileName = "${filePrefix}_${fileTimestamp.format(Date())}.csv"
            val file = File(context.cacheDir, fileName)

            FileWriter(file).use { writer ->
                writer.append("Date,Event ID,Source,Type,Title,Gross Amount (INR),Seller Cost (INR),Gateway Fee (INR),Tax (INR),Net Revenue (INR),Couple ID,User,Payment Method,Reference ID\n")

                for (rec in revenueRecords) {
                    val dateStr = if (rec.timestamp > 0) dateFormat.format(Date(rec.timestamp)) else "N/A"
                    val eventId = cleanCsv(rec.eventId.ifBlank { rec.id })
                    val source = cleanCsv(rec.source)
                    val type = cleanCsv(rec.type)
                    val title = cleanCsv(rec.title)
                    val gross = rec.grossAmountPaise / 100.0
                    val sellerCost = rec.sellerPayablePaise / 100.0
                    val gatewayFee = rec.gatewayFeePaise / 100.0
                    val tax = rec.taxPaise / 100.0
                    val net = rec.netRevenuePaise / 100.0
                    val coupleId = cleanCsv(rec.coupleId)
                    val user = cleanCsv(rec.userName.ifBlank { rec.userId })
                    val method = cleanCsv(rec.paymentMethod)
                    val ref = cleanCsv(rec.referenceId)

                    writer.append("\"$dateStr\",")
                    writer.append("\"$eventId\",")
                    writer.append("\"$source\",")
                    writer.append("\"$type\",")
                    writer.append("\"$title\",")
                    writer.append(String.format(Locale.US, "%.2f,", gross))
                    writer.append(String.format(Locale.US, "%.2f,", sellerCost))
                    writer.append(String.format(Locale.US, "%.2f,", gatewayFee))
                    writer.append(String.format(Locale.US, "%.2f,", tax))
                    writer.append(String.format(Locale.US, "%.2f,", net))
                    writer.append("\"$coupleId\",")
                    writer.append("\"$user\",")
                    writer.append("\"$method\",")
                    writer.append("\"$ref\"\n")
                }
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$filePrefix Export")
                putExtra(Intent.EXTRA_TEXT, "Exported ${revenueRecords.size} records from OurBloom Canonical Revenue Ledger.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Revenue Ledger CSV")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to export CSV: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun cleanCsv(str: String): String {
        return str.replace("\"", "\"\"").replace("\n", " ")
    }
}
