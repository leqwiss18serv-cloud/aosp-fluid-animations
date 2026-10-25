package cloud.leqwiss.aospfluid

import android.app.Application
import io.github.libxposed.service.XposedServiceHelper

class FluidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: io.github.libxposed.service.XposedService) {
                    ServiceHolder.bind(service)
                }

                override fun onServiceDied(service: io.github.libxposed.service.XposedService) {
                    ServiceHolder.unbind()
                }
            })
        }
    }
}
