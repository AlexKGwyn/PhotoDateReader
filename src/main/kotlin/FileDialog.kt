import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.AwtWindow
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FileDialog(
    parent: Frame? = null, title: String = "Open a file", onCloseRequest: (result: File?) -> Unit
) = AwtWindow(
    create = {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")

        val dialog = object : FileDialog(parent, title, LOAD) {

            override fun setVisible(value: Boolean) {
                super.setVisible(value)
                if (value) {
                    System.setProperty("apple.awt.fileDialogForDirectories", "false")
                    onCloseRequest(directory?.let { File(it, file) })
                }
            }
        }
        return@AwtWindow dialog
    }, dispose = FileDialog::dispose
)