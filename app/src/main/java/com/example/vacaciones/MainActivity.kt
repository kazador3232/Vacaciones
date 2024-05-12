@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.vacaciones

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.LocalDateTime
//La clase Pantalla mostrara los formularios.
enum class Pantalla {
    FORM,
    FOTO,
    LUGAR
}
//Esta clase almacena los permisos y las funciones de pantalla.
class CameraAppViewModel : ViewModel() {
    val pantalla = mutableStateOf(Pantalla.FORM)
    // callbacks
    var onPermisoCamaraOk : () -> Unit = {}
    var onPermisoUbicacionOk: () -> Unit = {}
    // lanzador permisos
    var lanzadorPermisos: ActivityResultLauncher<Array<String>>? = null
    fun cambiarPantallaFoto(){ pantalla.value = Pantalla.FOTO }
    fun cambiarPantallaForm(){ pantalla.value = Pantalla.FORM }
    fun cambiarPantallaLugar(){ pantalla.value = Pantalla.LUGAR }
}
//Esta clase almacena los valores del lugar.
class FormLugarViewModel : ViewModel() {
    val lugar      = mutableStateOf("")
    val latitud       = mutableStateOf(0.0)
    val longitud      = mutableStateOf(0.0)
    val fotoLugar = mutableStateOf<Uri?>(null)
}
class MainActivity : ComponentActivity() {
    val cameraAppVm:CameraAppViewModel by viewModels()
    lateinit var cameraController:LifecycleCameraController
    val lanzadorPermisos = //Aqui se solicitan los permisos necesarios.
        registerForActivityResult( ActivityResultContracts.RequestMultiplePermissions()) {
            when {
                (it[android.Manifest.permission.ACCESS_FINE_LOCATION] ?:
                false) or (it[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?:
                false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso ubicacion granted")
                            cameraAppVm.onPermisoUbicacionOk()
                }
                (it[android.Manifest.permission.CAMERA] ?: false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso camara granted")
                            cameraAppVm.onPermisoCamaraOk()
                }
                else -> {
                }
            }
        }
    private fun setupCamara() {
        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector =
            CameraSelector.DEFAULT_BACK_CAMERA
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraAppVm.lanzadorPermisos = lanzadorPermisos
        setupCamara()
        setContent {
            AppUI(cameraController)
        }
    }
}
//generador del nombre de la imagen
fun generarNombreSegunFechaHastaSegundo():String = LocalDateTime
    .now().toString().replace(Regex("[T:.-]"), "").substring(0, 14)
//Generador del archivo que utiliza el nombre creado por defecto
fun crearArchivoImagenPrivado(contexto: Context): File = File(
    contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
    "${generarNombreSegunFechaHastaSegundo()}.jpg"
)
fun uri2imageBitmap(uri:Uri, contexto:Context) =
    BitmapFactory.decodeStream(
        contexto.contentResolver.openInputStream(uri)
    ).asImageBitmap()
//Funcion que guarda la fotografia
fun tomarFotografia(cameraController: CameraController, archivo:File,
                    contexto:Context, imagenGuardadaOk:(uri:Uri)->Unit) {
    val outputFileOptions = ImageCapture.OutputFileOptions.Builder(archivo).build()
    cameraController.takePicture(outputFileOptions,
        ContextCompat.getMainExecutor(contexto), object: ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.also {
                    Log.v("tomarFotografia()::onImageSaved", "Foto guardada en ${it.toString()}")
                            imagenGuardadaOk(it)
                }
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e("tomarFotografia()", "Error: ${exception.message}")
            }
        })
}
class SinPermisoException(mensaje:String) : Exception(mensaje)
//Funcion para obtener la ubicacion
fun getUbicacion(contexto: Context, onUbicacionOk:(location: Location) -> Unit) {
    try {
        val servicio =
            LocationServices.getFusedLocationProviderClient(contexto)
        val tarea =
            servicio.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        tarea.addOnSuccessListener {
            onUbicacionOk(it)
        }
    } catch (e:SecurityException) {
        throw SinPermisoException(e.message?:"No tiene permisos para conseguir la ubicación")
    }
}
@Composable
fun AppUI(cameraController: CameraController) {
    val contexto = LocalContext.current
    val formLugarVm:FormLugarViewModel = viewModel()
    val cameraAppViewModel:CameraAppViewModel = viewModel()
    when(cameraAppViewModel.pantalla.value) {
        //Llama al formulario para gregar el nombre del lugar
        Pantalla.LUGAR -> {
            PantallaNombreUI(formLugarVm, cameraAppViewModel)
        }
        //Llama al formulario principal que contiene los botones clickeables
        Pantalla.FORM -> {
            PantallaFormUI(
                formLugarVm,
                agregarNombreOnClick = {
                    cameraAppViewModel.cambiarPantallaLugar()
                },
                tomarFotoOnClick = {
                    cameraAppViewModel.cambiarPantallaFoto()
                    cameraAppViewModel.lanzadorPermisos?.launch(arrayOf(android.Manifest.permission.CAMERA))
                },
                actualizarUbicacionOnClick = {
                    cameraAppViewModel.onPermisoUbicacionOk = {
                        getUbicacion(contexto) {
                            formLugarVm.latitud.value = it.latitude
                            formLugarVm.longitud.value = it.longitude
                        }
                    }
                    cameraAppViewModel.lanzadorPermisos?.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            )
        }
        //Muestra la camara
        Pantalla.FOTO -> {
            PantallaFotoUI(formLugarVm, cameraAppViewModel,
                cameraController)
        } else-> {
            Log.v("AppUI()", "when else, no debería entrar aquí")
        }
    }
}
@Composable
fun PantallaFormUI(
    formLugarVm:FormLugarViewModel,
    agregarNombreOnClick:() -> Unit = {},
    tomarFotoOnClick:() -> Unit = {},
    actualizarUbicacionOnClick:() -> Unit = {}
) {
    val contexto = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        //Boton para agregar nombre
        Button(onClick = {
            agregarNombreOnClick()
        }) {
            Text("Agregar nombre del lugar")
        }
        //Boton para abrir camara
        Button(onClick = {
            tomarFotoOnClick()
        }) {
            Text("Tomar Fotografía del lugar")
        }
        //Aqui se imprime la imagen, el nombre y las coordenadas del lugar despues de tomar la fotografia
        formLugarVm.fotoLugar.value?.also {
            Box(Modifier.size(200.dp, 100.dp)) {
                Image(
                    painter = BitmapPainter(uri2imageBitmap(it,
                        contexto)),
                    contentDescription = "Imagen recepción encomienda ${formLugarVm.lugar.value}"
                )
            }
            Text("Nombre del lugar: ${formLugarVm.lugar.value}")
            Text("Las coordenadas son: lat: ${formLugarVm.latitud.value} y long: ${formLugarVm.longitud.value}")
        }
        //Boton para obtener la ubicacion
        Button(onClick = {
            actualizarUbicacionOnClick()
        }) {
            Text("Agregar coordenadas")
        }
        Spacer(Modifier.height(100.dp))
        MapaOsmUI(formLugarVm.latitud.value,
            formLugarVm.longitud.value)
    }
}
//Funcion que muestra la camara y un boton para tomar la foto
@Composable
fun PantallaFotoUI(formLugarVm:FormLugarViewModel, appViewModel:
CameraAppViewModel, cameraController: CameraController
) {
    val contexto = LocalContext.current
    AndroidView(
        factory = {
            PreviewView(it).apply {
                controller = cameraController
            }
        },
        modifier = Modifier.fillMaxSize()
    )
    Button(onClick = {
        tomarFotografia(
            cameraController,
            crearArchivoImagenPrivado(contexto),
            contexto
        ) {//Se crea el archivo de la imagen y se pasa como parametro al ViewModel
            formLugarVm.fotoLugar.value = it
            appViewModel.cambiarPantallaForm()
        }
    }) {
        Text("Tomar foto")
    }
}
//Se agrega el nombre del lugar y se pasa como parametro al ViewModel
@Composable
fun PantallaNombreUI(formLugarVm:FormLugarViewModel, appViewModel: CameraAppViewModel) {
    val contexto = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            label = { Text("Ingrese nombre del lugar") },
            value = formLugarVm.lugar.value,
            onValueChange = {formLugarVm.lugar.value = it},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        )
        Button(onClick =
        {
            val it = formLugarVm.lugar.value
            formLugarVm.lugar.value = it
            appViewModel.cambiarPantallaForm()
        }
        ) {
            Text("Agregar nombre")
        }
    }
}
//Esta funcion muestra muestra el mapa con Open Street Map
@Composable
fun MapaOsmUI(latitud:Double, longitud:Double) {
    val contexto = LocalContext.current
    AndroidView(
        factory = {
            MapView(it).also {
                it.setTileSource(TileSourceFactory.MAPNIK)
                Configuration.getInstance().userAgentValue =
                    contexto.packageName
            }
        }, update = {
            it.overlays.removeIf { true }
            it.invalidate()
            it.controller.setZoom(18.0)
            val geoPoint = GeoPoint(latitud, longitud)
            it.controller.animateTo(geoPoint)
            val marcador = Marker(it)
            marcador.position = geoPoint
            marcador.setAnchor(Marker.ANCHOR_CENTER,
                Marker.ANCHOR_CENTER)
            it.overlays.add(marcador)
        }
    )
}
