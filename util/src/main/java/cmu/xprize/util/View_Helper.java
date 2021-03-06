/**
 Copyright 2015 Kevin Willows
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package cmu.xprize.util;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

public class View_Helper {

    static final private String TAG= "View_Helper";

    static public View getViewById(int findme, ViewGroup container) {
        View foundView = null;

        if(container != null) {

            try {
                for (int i = 0; (foundView == null) && (i < container.getChildCount()); ++i) {

                    View nextChild = (View) container.getChildAt(i);

                    if (((View) nextChild).getId() == findme) {
                        foundView = nextChild;
                        break;
                    } else {
                        if (nextChild instanceof ViewGroup)
                            foundView = getViewById(findme, (ViewGroup) nextChild);
                    }
                }
            } catch (Exception e) {
                Log.i(TAG, "View walk error: " + e);
            }
        }
        return foundView;
    }


}
