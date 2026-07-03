package li.mofanx.sctrl.shizuku;

import android.graphics.Bitmap;
import android.graphics.Rect;
import li.mofanx.sctrl.shizuku.CommandResult;

interface IUserService {
    void destroy() = 16777114; // Destroy method defined by Shizuku server
    void exit() = 1;
    CommandResult execCommand(String command) = 2;
    Bitmap takeScreenshot1(int width, int height) = 3;
    Bitmap takeScreenshot2(in Rect crop, int rotation) = 4;
    Bitmap takeScreenshot3(in Rect crop) = 5;
    int killLegacyService() = 6;
    boolean setDisplayPowerMode(boolean turnOff) = 7;
    boolean setStayAwake(boolean enable) = 8;
    boolean isKeepingScreenOff() = 9;
}
