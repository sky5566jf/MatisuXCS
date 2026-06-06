package com.matisu.xcs

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import com.matisu.xcs.service.ControlService

class ScreenCaptureActivity : Activity() {

    companion object {
        const val ACTION_RESULT = "com.matisu.xcs.action.RESULT"
        const val REQUEST_CODE = 100

        fun request(context: Context) {
            val intent = Intent(context, ScreenCaptureActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mpManager.createScreenCaptureIntent(),
            REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            val serviceIntent = Intent(this, ControlService::class.java).apply {
                action = ACTION_RESULT
                putExtra(ControlService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ControlService.EXTRA_DATA, data)
            }
            startForegroundService(serviceIntent)
        }
        finish()
    }
}
