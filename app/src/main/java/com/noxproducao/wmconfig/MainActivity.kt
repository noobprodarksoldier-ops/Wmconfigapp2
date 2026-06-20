package com.noxproducao.wmconfig

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var densityInput: EditText
    private lateinit var sizeInput: EditText
    private lateinit var statusText: TextView

    private val permissionRequestCode = 1001

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == permissionRequestCode) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                setStatus("Shizuku autorizado. Pronto pra usar.")
            } else {
                setStatus("Permissão do Shizuku negada. O app não funciona sem ela.")
            }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkShizukuPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("wm_config", MODE_PRIVATE)

        densityInput = findViewById(R.id.densityInput)
        sizeInput = findViewById(R.id.sizeInput)
        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.applyButton).setOnClickListener { applyCurrentInputs() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { saveConfig() }
        findViewById<Button>(R.id.loadButton).setOnClickListener { loadAndApplySaved() }
        findViewById<Button>(R.id.resetButton).setOnClickListener { resetToDefault() }

        loadSavedIntoFields()

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    private fun checkShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            setStatus("Shizuku não está rodando. Abra o app Shizuku e inicie o serviço.")
            return
        }
        if (Shizuku.isPreV11()) {
            setStatus("Versão do Shizuku muito antiga, atualize o app Shizuku.")
            return
        }
        when {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                setStatus("Shizuku pronto.")
            }
            Shizuku.shouldShowRequestPermissionRationale() -> {
                setStatus("Permissão negada antes. Ative manualmente no app Shizuku.")
            }
            else -> {
                Shizuku.requestPermission(permissionRequestCode)
            }
        }
    }

    private fun loadSavedIntoFields() {
        val savedDensity = prefs.getString("density", "")
        val savedSize = prefs.getString("size", "")
        if (!savedDensity.isNullOrEmpty()) densityInput.setText(savedDensity)
        if (!savedSize.isNullOrEmpty()) sizeInput.setText(savedSize)
    }

    private fun applyCurrentInputs() {
        val density = densityInput.text.toString().trim()
        val size = sizeInput.text.toString().trim()

        if (density.isEmpty() && size.isEmpty()) {
            setStatus("Preencha densidade e/ou tamanho.")
            return
        }

        var ok = true
        if (density.isNotEmpty()) {
            ok = runShellCommand("wm density $density") && ok
        }
        if (size.isNotEmpty()) {
            ok = runShellCommand("wm size $size") && ok
        }
        setStatus(if (ok) "Configuração aplicada." else "Erro ao aplicar (veja o Logcat).")
    }

    private fun saveConfig() {
        val density = densityInput.text.toString().trim()
        val size = sizeInput.text.toString().trim()
        prefs.edit()
            .putString("density", density)
            .putString("size", size)
            .apply()
        setStatus("Configuração salva.")
    }

    private fun loadAndApplySaved() {
        loadSavedIntoFields()
        applyCurrentInputs()
    }

    private fun resetToDefault() {
        val ok1 = runShellCommand("wm density reset")
        val ok2 = runShellCommand("wm size reset")
        setStatus(if (ok1 && ok2) "Resetado ao padrão." else "Erro ao resetar (veja o Logcat).")
    }

    private fun runShellCommand(command: String): Boolean {
        return try {
            val newProcess: Method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcess.isAccessible = true
            val process = newProcess.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun setStatus(message: String) {
        runOnUiThread { statusText.text = message }
    }
}
