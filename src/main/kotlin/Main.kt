import Processor.toHSVImage
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileFilter
import java.text.SimpleDateFormat
import java.util.*


private val extensions = setOf("jpg", "jpeg", "png")

@Composable
@Preview
fun App() {
    var state by remember { mutableStateOf<State>(State.NoFolderSelected) }
    var fileSelectorOpen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()


    LaunchedEffect(state) {
        println(state)
    }

    MaterialTheme(colors = LightColors, typography = Typography) {
        val scaffoldState = rememberScaffoldState()
        Scaffold(scaffoldState = scaffoldState) {

            if (fileSelectorOpen) {
                FileDialog {
                    fileSelectorOpen = false
                    if (it == null) {
                        coroutineScope.launch {
                            scaffoldState.snackbarHostState.showSnackbar("No Folder Selected")
                        }
                    } else if (!it.isDirectory) {
                        coroutineScope.launch {
                            scaffoldState.snackbarHostState.showSnackbar("Please select a folder")
                        }
                    } else {
                        state = State.LoadingFolder(it)
                    }
                }
            }

            val currentState = state

            if (currentState is State.LoadingFolder) {
                LaunchedEffect(state) {
                    val files =
                        currentState.path.listFiles(FileFilter { extensions.contains(it.extension) })
                    state = State.Loaded(currentState.path, files?.toList() ?: emptyList())
                }
            }

            Column {
                TopAppBar(title = {
                    Text("Photo Date Reader")
                })
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (currentState) {
                        State.NoFolderSelected -> {
                            Text("No Folder Selected")
                            Spacer(Modifier.width(24.dp))
                            Button(onClick = { fileSelectorOpen = true }) {
                                Text("Open Folder")
                            }
                        }

                        is State.LoadingFolder -> {
                            Text("Scanning Folder ${currentState.path.name.ellipsize(10)}")
                            LinearProgressIndicator(modifier = Modifier.width(48.dp))
                        }

                        is State.Loaded -> {
                            Text("Loaded Folder \"${currentState.path.name.ellipsize(10)}\", found ${currentState.fileList.size} images.")
                        }
                    }
                }
                if (currentState is State.Loaded) {
                    ImageList(currentState, modifier = Modifier.fillMaxSize())
                }
            }
        }

    }
}

@Composable
fun ImageList(
    state: State.Loaded,
    modifier: Modifier = Modifier,
    onImageClicked: (file: File) -> Unit = { _ -> }
) {
    val imageLoader = remember(state) { ImageLoader(100) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy") }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(12.dp, 8.dp)) {
        items(items = state.fileList, key = { file: File -> file.path }) { imageFile ->
            var image by remember { mutableStateOf<ImageBitmap?>(null) }
            var processResult by remember { mutableStateOf<Date?>(null) }
            LaunchedEffect(imageFile.path) {
                val bufferedImage = imageLoader.loadImage(imageFile)
                image = Processor.processImage(bufferedImage).toComposeImageBitmap()
                //image = Processor.thresholdImage(bufferedImage).toHSVImage().toComposeImageBitmap()
            }
            Row(
                modifier.fillMaxWidth().heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(800.dp), contentAlignment = Alignment.Center) {
                    val currentImage = image
                    if (currentImage == null) {
                        CircularProgressIndicator()
                    } else {
                        Image(
                            currentImage,
                            "",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxSize().clickable {
                                onImageClicked(imageFile)
                            })
                    }
                }
                Text(imageFile.name.ellipsize(10))

                val resultDate = processResult?.let { dateFormat.format(it) } ?: "Processing"
                Text(resultDate)
            }
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}


private val LightColors = lightColors(
    primary = Color(0xFF335d5d),
    primaryVariant = Color(0xFF66A3Cd),
    background = Color(0xFFF0F0F0)
)

private val Typography = Typography(
    body1 = TextStyle(fontSize = 14.sp),
    body2 = TextStyle(fontSize = 14.sp),
    h1 = TextStyle(fontSize = 16.sp),
    h2 = TextStyle(fontSize = 15.sp),
    h3 = TextStyle(fontSize = 14.sp),
    h4 = TextStyle(fontSize = 14.sp),
    button = TextStyle(fontSize = 14.sp)
)


sealed interface State {
    data object NoFolderSelected : State
    data class LoadingFolder(val path: File, val recursive: Boolean = false) : State
    data class Loaded(val path: File, val fileList: List<File>) : State
    data class Error(val error: String)
}

fun String.ellipsize(maxChars: Int = 10): String {
    return if (length < maxChars) {
        this
    } else {
        this.substring(0, maxChars - 1) + "…"
    }
}