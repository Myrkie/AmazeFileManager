package com.amaze.filemanager.filesystem.root

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import com.amaze.filemanager.R
import java.io.File



/**
 * Creates a document provider for root files.
 */
class RootDocumentsProvider : DocumentsProvider() {

    companion object {
        const val ROOT_ID = "root"
        const val AUTHORITY = "com.amaze.filemanager.documents"
    }

    override fun onCreate(): Boolean = true

    private fun getPathFromDocumentId(documentId: String): String {
        return if (documentId == ROOT_ID) "/" else documentId
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(
            projection ?: arrayOf(
                DocumentsContract.Root.COLUMN_ROOT_ID,
                DocumentsContract.Root.COLUMN_TITLE,
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                DocumentsContract.Root.COLUMN_ICON
            )
        )

        val row = cursor.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
        row.add(DocumentsContract.Root.COLUMN_TITLE, context?.getString(R.string.root_mode))
        row.add(
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.FLAG_SUPPORTS_CREATE or DocumentsContract.Root.FLAG_LOCAL_ONLY
        )
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_ID)
        row.add(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_manage)

        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val path = getPathFromDocumentId(documentId)
        val file = File(path)

        val cursor = MatrixCursor(projection ?: arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        ))

        val row = cursor.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name.ifEmpty { "/" })
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE,
            if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream")
        row.add(DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.FLAG_SUPPORTS_DELETE or DocumentsContract.Document.FLAG_SUPPORTS_WRITE)
        row.add(DocumentsContract.Document.COLUMN_SIZE, file.length())
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())

        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val path = getPathFromDocumentId(parentDocumentId)

        val cursor = MatrixCursor(projection ?: arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        ))

        try {
            ListFilesCommand.listFiles(
                path = path,
                root = true,
                showHidden = true,
                openModeCallback = {},
                onFileFoundCallback = { file ->
                    // Skip '.' and '..'
                    if (file.name == "." || file.name == "..") return@listFiles

                    val actualFile = if (file.link.isNotBlank()) File(file.link) else File(file.path)
                    val name = actualFile.name

                    val row = cursor.newRow()
                    row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, actualFile.path)
                    row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name)
                    row.add(DocumentsContract.Document.COLUMN_SIZE, file.getSize())
                    row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
                    row.add(DocumentsContract.Document.COLUMN_MIME_TYPE,
                        if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream")
                    row.add(DocumentsContract.Document.COLUMN_FLAGS,
                        DocumentsContract.Document.FLAG_SUPPORTS_DELETE or DocumentsContract.Document.FLAG_SUPPORTS_WRITE)
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return cursor
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val path = getPathFromDocumentId(documentId)
        return try {
            val file = File(path)
            if (file.canRead()) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                ReadRootFileCommand.readFile(path, ParcelFileDescriptor.MODE_READ_ONLY, signal)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Cannot open document $documentId")
        }
    }


    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String? {
        val parentPath = getPathFromDocumentId(parentDocumentId)
        val path = "$parentPath/$displayName"

        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            MakeDirectoryCommand.makeDirectory(parentPath, displayName)
        } else {
            MakeFileCommand.makeFile(path)
        }

        return path
    }

    override fun deleteDocument(documentId: String) {
        val path = getPathFromDocumentId(documentId)
        DeleteFileCommand.deleteFile(path)
    }

    override fun renameDocument(documentId: String, displayName: String): String? {
        val path = getPathFromDocumentId(documentId)
        val newPath = File(path).parent + "/" + displayName
        return if (RenameFileCommand.renameFile(path, newPath)) newPath else null
    }
}
