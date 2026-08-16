package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.winlator.box64.Box64Preset;
import com.winlator.box64.Box64PresetManager;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.Drive;
import com.winlator.contentdialog.AddEnvVarDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.EnvVars;
import com.winlator.core.FileUtils;
import com.winlator.core.KeyValueSet;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.StringUtils;
import com.winlator.core.WineInfo;
import com.winlator.core.WineInstaller;
import com.winlator.core.WineUtils;
import com.winlator.widget.CPUListView;
import com.winlator.widget.EnvVarsView;
import com.winlator.win32.WinVersions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

public class ContainerDetailFragment extends Fragment {
    private static final int OPEN_EXEC_PATH_REQUEST_CODE = 10;
    private static final int OPEN_WORKING_DIR_REQUEST_CODE = 11;
    private ContainerManager manager;
    private final int containerId;
    private Container container;
    private PreloaderDialog preloaderDialog;
    private Callback<String> openDirectoryCallback;
    private Callback<String> openExecPathCallback;
    private Callback<String> openWorkingDirCallback;

    public ContainerDetailFragment() {
        this(0);
    }

    public ContainerDetailFragment(int containerId) {
        this.containerId = containerId;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            String path = FileUtils.getFilePathFromUri(data.getData());
            if (path != null) {
                if (requestCode == OPEN_EXEC_PATH_REQUEST_CODE && openExecPathCallback != null) openExecPathCallback.call(path);
                else if (requestCode == OPEN_WORKING_DIR_REQUEST_CODE && openWorkingDirCallback != null) openWorkingDirCallback.call(path);
            }
        }

        if (requestCode == MainActivity.OPEN_DIRECTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                String path = FileUtils.getFilePathFromUri(data.getData());
                if (path != null && openDirectoryCallback != null) openDirectoryCallback.call(path);
            }
            openDirectoryCallback = null;
        }
        else if (requestCode == OPEN_EXEC_PATH_REQUEST_CODE) openExecPathCallback = null;
        else if (requestCode == OPEN_WORKING_DIR_REQUEST_CODE) openWorkingDirCallback = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(isEditMode() ? R.string.edit_container : R.string.new_container);
    }

    public boolean isEditMode() {
        return container != null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup root, @Nullable Bundle savedInstanceState) {
        final Context context = getContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final View view = inflater.inflate(R.layout.container_detail_fragment, root, false);
        manager = new ContainerManager(context);
        container = containerId > 0 ? manager.getContainerById(containerId) : null;

        final EditText etName = view.findViewById(R.id.ETName);

        if (isEditMode()) {
            etName.setText(container.getName());
        }
        else etName.setText(getString(R.string.container)+"-"+manager.getNextContainerId());

        final EditText etExecPath = view.findViewById(R.id.ETExecPath);
        etExecPath.setText(isEditMode() ? container.getExecPath() : "");

        final EditText etWorkingDir = view.findViewById(R.id.ETWorkingDir);
        etWorkingDir.setText(isEditMode() ? container.getWorkingDir() : "");

        view.findViewById(R.id.BTBrowseExecPath).setOnClickListener((v) -> {
            openExecPathCallback = etExecPath::setText;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, OPEN_EXEC_PATH_REQUEST_CODE);
        });

        view.findViewById(R.id.BTBrowseWorkingDir).setOnClickListener((v) -> {
            openWorkingDirCallback = etWorkingDir::setText;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(Environment.getExternalStorageDirectory()));
            startActivityForResult(intent, OPEN_WORKING_DIR_REQUEST_CODE);
        });

        final EditText etCliPort = view.findViewById(R.id.ETCliPort);
        etCliPort.setText(String.valueOf(isEditMode() ? container.getCliPort() : Container.CLI_PORT_DEFAULT));

        final ArrayList<WineInfo> wineInfos = WineInstaller.getInstalledWineInfos(context);
        final Spinner sWineVersion = view.findViewById(R.id.SWineVersion);
        if (wineInfos.size() > 1) loadWineVersionSpinner(view, sWineVersion, wineInfos);

        final Spinner sStartupSelection = view.findViewById(R.id.SStartupSelection);
        byte oldStartupSelection = isEditMode() ? container.getStartupSelection() : -1;
        sStartupSelection.setSelection(oldStartupSelection != -1 ? oldStartupSelection : Container.STARTUP_SELECTION_ESSENTIAL);

        sWinVersion = view.findViewById(R.id.SWinVersion);
        sWinVersion.setTag((byte)-1);

        final Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        Box64PresetManager.loadSpinner(sBox64Preset, isEditMode() ? container.getBox64Preset() : preferences.getString("box64_preset", Box64Preset.DEFAULT));

        final CPUListView cpuListView = view.findViewById(R.id.CPUListView);
        final CPUListView cpuListViewWoW64 = view.findViewById(R.id.CPUListViewWoW64);

        cpuListView.setCheckedCPUList(isEditMode() ? container.getCPUList(true) : Container.getFallbackCPUList());
        cpuListViewWoW64.setCheckedCPUList(isEditMode() ? container.getCPUListWoW64(true) : Container.getFallbackCPUList());

        final EnvVarsView envVarsView = createEnvVarsTab(view);
        createDrivesTab(view);

        AppUtils.setupTabLayout(view, R.id.TabLayout, (tabResId) -> {
            if (tabResId == R.id.LLTabAdvanced) if ((byte)sWinVersion.getTag() == -1) WinVersions.loadSpinner(container, sWinVersion);
        }, R.id.LLTabEnvVars, R.id.LLTabDrives, R.id.LLTabAdvanced);

        view.findViewById(R.id.BTConfirm).setOnClickListener((v) -> {
            try {
                String name = etName.getText().toString();
                String envVars = envVarsView.getEnvVars();
                String drives = getDrives(view);
                String cpuList = cpuListView.getCheckedCPUListAsString();
                String cpuListWoW64 = cpuListViewWoW64.getCheckedCPUListAsString();
                byte startupSelection = (byte)sStartupSelection.getSelectedItemPosition();
                String box64Preset = Box64PresetManager.getSpinnerSelectedId(sBox64Preset);
                String execPath = etExecPath.getText().toString().trim();
                String workingDir = etWorkingDir.getText().toString().trim();
                int cliPort = parsePort(etCliPort.getText().toString().trim());

                if (isEditMode()) {
                    container.setName(name);
                    container.setEnvVars(envVars);
                    container.setCPUList(cpuList);
                    container.setCPUListWoW64(cpuListWoW64);
                    container.setDrives(drives);
                    container.setStartupSelection(startupSelection);
                    container.setBox64Preset(box64Preset);
                    container.setExecPath(execPath);
                    container.setWorkingDir(workingDir);
                    container.setCliPort(cliPort);
                    container.saveData();

                    saveWinVersion();

                    getActivity().onBackPressed();
                }
                else {
                    JSONObject data = new JSONObject();
                    data.put("name", name);
                    data.put("envVars", envVars);
                    data.put("cpuList", cpuList);
                    data.put("cpuListWoW64", cpuListWoW64);
                    data.put("drives", drives);
                    data.put("startupSelection", startupSelection);
                    data.put("box64Preset", box64Preset);
                    data.put("execPath", execPath);
                    data.put("workingDir", workingDir);
                    data.put("cliPort", cliPort);

                    if (wineInfos.size() > 1) {
                        data.put("wineVersion", wineInfos.get(sWineVersion.getSelectedItemPosition()).identifier());
                    }

                    preloaderDialog.show(R.string.creating_container);
                    manager.createContainerAsync(data, (container) -> {
                        if (container != null) {
                            this.container = container;
                            saveWinVersion();
                        }
                        preloaderDialog.close();
                        getActivity().onBackPressed();
                    });
                }
            }
            catch (JSONException e) {}
        });
        return view;
    }

    private Spinner sWinVersion;

    private int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e) {
            return Container.CLI_PORT_DEFAULT;
        }
    }

    private void saveWinVersion() {
        int oldPosition = (byte)sWinVersion.getTag();
        if (oldPosition != -1) {
            int newPosition = sWinVersion.getSelectedItemPosition();
            if (oldPosition != newPosition) WineUtils.setWinVersion(container, newPosition);
        }
    }

    public static String getScreenSize(View view) {
        Spinner sScreenSize = view.findViewById(R.id.SScreenSize);
        String value = sScreenSize.getSelectedItem().toString();
        if (sScreenSize.getSelectedItemPosition() == 0) {
            value = Container.DEFAULT_SCREEN_SIZE;
            String strWidth = ((EditText)view.findViewById(R.id.ETScreenWidth)).getText().toString().trim();
            String strHeight = ((EditText)view.findViewById(R.id.ETScreenHeight)).getText().toString().trim();
            if (strWidth.matches("[0-9]+") && strHeight.matches("[0-9]+")) {
                int width = Integer.parseInt(strWidth);
                int height = Integer.parseInt(strHeight);
                if ((width % 2) == 0 && (height % 2) == 0) return width+"x"+height;
            }
        }
        return StringUtils.parseIdentifier(value);
    }

    public static void loadScreenSizeSpinner(View view, String selectedValue) {
        final Spinner sScreenSize = view.findViewById(R.id.SScreenSize);

        final LinearLayout llCustomScreenSize = view.findViewById(R.id.LLCustomScreenSize);
        sScreenSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                llCustomScreenSize.setVisibility(sScreenSize.getSelectedItemPosition() == 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        boolean found = AppUtils.setSpinnerSelectionFromIdentifier(sScreenSize, selectedValue);
        if (!found) {
            sScreenSize.setSelection(0);
            String[] screenSize = selectedValue.split("x");
            ((EditText)view.findViewById(R.id.ETScreenWidth)).setText(screenSize[0]);
            ((EditText)view.findViewById(R.id.ETScreenHeight)).setText(screenSize[1]);
        }
    }

    public static String getWinComponents(View view) {
        ViewGroup parent = view.findViewById(R.id.LLTabWinComponents);
        ArrayList<View> views = new ArrayList<>();
        AppUtils.findViewsWithClass(parent, Spinner.class, views);
        String[] wincomponents = new String[views.size()];

        for (int i = 0; i < views.size(); i++) {
            Spinner spinner = (Spinner)views.get(i);
            wincomponents[i] = spinner.getTag()+"="+spinner.getSelectedItemPosition();
        }
        return String.join(",", wincomponents);
    }

    public static void createWinComponentsTab(View view, String wincomponents) {
        Context context = view.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        ViewGroup tabView = view.findViewById(R.id.LLTabWinComponents);
        ViewGroup directxSectionView = tabView.findViewById(R.id.LLWinComponentsDirectX);
        ViewGroup generalSectionView = tabView.findViewById(R.id.LLWinComponentsGeneral);

        for (String[] wincomponent : new KeyValueSet(wincomponents)) {
            final String name = wincomponent[0];
            ViewGroup parent = name.startsWith("direct") || name.startsWith("x") ? directxSectionView : generalSectionView;
            View itemView = inflater.inflate(R.layout.wincomponent_list_item, parent, false);
            ((android.widget.TextView)itemView.findViewById(R.id.TextView)).setText(StringUtils.getString(context, name));
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            spinner.setSelection(Integer.parseInt(wincomponent[1]), false);
            spinner.setTag(name);
            parent.addView(itemView);
        }
    }

    private EnvVarsView createEnvVarsTab(final View view) {
        final Context context = view.getContext();
        final EnvVarsView envVarsView = view.findViewById(R.id.EnvVarsView);
        envVarsView.setEnvVars(new EnvVars(isEditMode() ? container.getEnvVars() : Container.DEFAULT_ENV_VARS));
        view.findViewById(R.id.BTAddEnvVar).setOnClickListener((v) -> (new AddEnvVarDialog(context, envVarsView)).show());
        return envVarsView;
    }

    private String getDrives(View view) {
        LinearLayout parent = view.findViewById(R.id.LLDrives);
        String drives = "";

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            Spinner spinner = child.findViewById(R.id.Spinner);
            EditText editText = child.findViewById(R.id.EditText);
            String path = editText.getText().toString().replace(":", "").trim();
            if (!path.isEmpty()) drives += spinner.getSelectedItem()+path;
        }
        return drives;
    }

    private void createDrivesTab(View view) {
        final Context context = getContext();

        final LinearLayout parent = view.findViewById(R.id.LLDrives);
        final View emptyTextView = view.findViewById(R.id.TVDrivesEmptyText);
        LayoutInflater inflater = LayoutInflater.from(context);
        final String drives = isEditMode() ? container.getDrives() : Container.DEFAULT_DRIVES;
        final String[] driveLetters = new String[Container.MAX_DRIVE_LETTERS];
        for (int i = 0; i < driveLetters.length; i++) driveLetters[i] = ((char)(i + 68))+":";

        Callback<Drive> addItem = (drive) -> {
            final View itemView = inflater.inflate(R.layout.drive_list_item, parent, false);
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, driveLetters));
            AppUtils.setSpinnerSelectionFromValue(spinner, drive.letter+":");

            final EditText editText = itemView.findViewById(R.id.EditText);
            editText.setText(drive.path);

            itemView.findViewById(R.id.BTSearch).setOnClickListener((v) -> showDriveSearchPopupMenu(v, drive, editText));
            itemView.findViewById(R.id.BTRemove).setOnClickListener((v) -> {
                parent.removeView(itemView);
                if (parent.getChildCount() == 0) emptyTextView.setVisibility(View.VISIBLE);
            });
            parent.addView(itemView);
        };
        for (Drive drive : Container.drivesIterator(drives)) addItem.call(drive);

        view.findViewById(R.id.BTAddDrive).setOnClickListener((v) -> {
            if (parent.getChildCount() >= Container.MAX_DRIVE_LETTERS) return;
            final String nextDriveLetter = String.valueOf(driveLetters[parent.getChildCount()].charAt(0));
            addItem.call(new Drive(nextDriveLetter, ""));
        });

        if (drives.isEmpty()) emptyTextView.setVisibility(View.VISIBLE);
    }

    private void showDriveSearchPopupMenu(View anchorView, final Drive drive, final EditText editText) {
        final FragmentActivity activity = getActivity();
        final Fragment $this = ContainerDetailFragment.this;

        PopupMenu popupMenu = new PopupMenu(activity, anchorView);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) popupMenu.setForceShowIcon(true);
        popupMenu.inflate(R.menu.drive_search_popup_menu);
        Menu menu = popupMenu.getMenu();
        SubMenu subMenu = menu.findItem(R.id.menu_item_locations).getSubMenu();
        ArrayList<Container> containers = manager.getContainers();
        for (int i = 0; i < containers.size(); i++) {
            Container container = containers.get(i);
            subMenu.add(0, 0, container.id, container.getName()+" (Drive C:)");
        }

        popupMenu.setOnMenuItemClickListener((menuItem) -> {
            int itemId = menuItem.getItemId();
            switch (itemId) {
                case R.id.menu_item_open_directory:
                    openDirectoryCallback = (path) -> {
                        drive.path = path;
                        editText.setText(path);
                    };

                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(Environment.getExternalStorageDirectory()));
                    activity.startActivityFromFragment($this, intent, MainActivity.OPEN_DIRECTORY_REQUEST_CODE);
                    break;
                case R.id.menu_item_downloads:
                    drive.path = AppUtils.DIRECTORY_DOWNLOADS;
                    editText.setText(AppUtils.DIRECTORY_DOWNLOADS);
                    break;
                case R.id.menu_item_internal_storage:
                    drive.path = AppUtils.INTERNAL_STORAGE;
                    editText.setText(AppUtils.INTERNAL_STORAGE);
                    break;
                default:
                    Container container = manager.getContainerById(menuItem.getOrder());
                    if (container != null) {
                        String path = container.getRootDir()+"/.wine/drive_c";
                        drive.path = path;
                        editText.setText(path);
                    }
                    break;
            }
            return true;
        });

        popupMenu.show();
    }

    private void loadWineVersionSpinner(final View view, Spinner sWineVersion, final ArrayList<WineInfo> wineInfos) {
        final Context context = getContext();
        sWineVersion.setEnabled(!isEditMode());
        view.findViewById(R.id.LLWineVersion).setVisibility(View.VISIBLE);
        sWineVersion.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, wineInfos));
        if (isEditMode()) AppUtils.setSpinnerSelectionFromValue(sWineVersion, WineInfo.fromIdentifier(context, container.getWineVersion()).toString());
    }
}
