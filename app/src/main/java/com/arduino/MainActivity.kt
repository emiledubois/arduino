package com.arduino

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager // <-- IMPORTACIÓN NECESARIA
import android.content.Context            // <-- IMPORTACIÓN NECESARIA
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.arduino.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // --- CORRECCIÓN 1: FORMA MODERNA DE OBTENER EL BLUETOOTH ADAPTER ---
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    // --------------------------------------------------------------------

    private var dispositivoSeleccionado: BluetoothDevice? = null

    // ... (El código de los launchers no cambia, está bien)
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            cargarDispositivos()
        } else {
            Toast.makeText(this, "Bluetooth es necesario", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            verificarYActivarBluetooth()
        } else {
            Toast.makeText(this, "Permisos necesarios para Bluetooth", Toast.LENGTH_LONG).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupListeners()
        solicitarPermisos()
    }

    private fun setupObservers() {
        viewModel.estadoConexion.observe(this) { estado ->
            // ... (Sin cambios aquí)
            binding.tvEstadoConexion.text = estado
            val conectado = estado.contains("Conectado a")
            binding.btnConectar.isEnabled = !conectado
            binding.btnDesconectar.isEnabled = conectado
            binding.btnLeerSensores.isEnabled = conectado
            binding.btnEnviarThingSpeak.isEnabled = conectado
            binding.switchLecturaAutomatica.isEnabled = conectado
            if (conectado) {
                binding.tvEstadoConexion.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            } else {
                binding.tvEstadoConexion.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
        }

        // --- CORRECCIÓN 2: ARREGLAR LA LÍNEA 88 ---
        viewModel.dispositivosEmparejados.observe(this) { dispositivos ->
            // Primero, nos aseguramos de tener los permisos
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // Si no tenemos permisos aquí, no podemos obtener los nombres.
                // Podríamos mostrar una lista vacía o con un mensaje de error.
                return@observe
            }
            // Ahora sí, creamos el adaptador
            val deviceNames = dispositivos.map { it.name ?: "Dispositivo Desconocido" }
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                deviceNames
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerDispositivos.adapter = adapter
        }
        // -------------------------------------------

        viewModel.sensor1Value.observe(this) { valor ->
            binding.tvSensor1.text = valor
        }

        viewModel.sensor2Value.observe(this) { valor ->
            binding.tvSensor2.text = valor
        }

        viewModel.ultimoEnvioThingSpeak.observe(this) { estado ->
            binding.tvEstadoThingSpeak.text = estado
        }

        viewModel.lecturaAutomaticaActiva.observe(this) { activa ->
            binding.switchLecturaAutomatica.isChecked = activa
        }
    }

    // ... (El resto del código como lo tenías está bien)

    private fun setupListeners() {
        binding.spinnerDispositivos.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val dispositivos = viewModel.dispositivosEmparejados.value
                if (!dispositivos.isNullOrEmpty() && position < dispositivos.size) {
                    dispositivoSeleccionado = dispositivos[position]
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                dispositivoSeleccionado = null
            }
        }
        binding.btnConectar.setOnClickListener {
            dispositivoSeleccionado?.let { device ->
                viewModel.conectarDispositivo(device)
            } ?: run {
                Toast.makeText(this, "Seleccione un dispositivo", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnDesconectar.setOnClickListener { viewModel.desconectar() }
        binding.btnLeerSensores.setOnClickListener { viewModel.leerSensores() }
        binding.btnEnviarThingSpeak.setOnClickListener { viewModel.enviarDatosThingSpeak() }
        binding.switchLecturaAutomatica.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewModel.iniciarLecturaAutomatica()
            } else {
                viewModel.detenerLecturaAutomatica()
            }
        }
    }

    private fun solicitarPermisos() {
        val permisosNecesarios = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION // Añadido para escaneo en versiones antiguas
            )
        }
        val permisosPendientes = permisosNecesarios.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permisosPendientes.isNotEmpty()) {
            requestPermissionsLauncher.launch(permisosPendientes.toTypedArray())
        } else {
            verificarYActivarBluetooth()
        }
    }

    private fun verificarYActivarBluetooth() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no disponible", Toast.LENGTH_LONG).show()
            return
        }

        // Ya que solicitarPermisos() nos asegura tenerlos, esta verificación extra es redundante,
        // pero la dejamos como una doble seguridad.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            solicitarPermisos()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            cargarDispositivos()
        }
    }

    private fun cargarDispositivos() {
        // También verificamos permiso aquí, justo antes de la acción
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            solicitarPermisos()
            return
        }
        viewModel.cargarDispositivosEmparejados()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.desconectar()
    }
}
