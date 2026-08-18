package com.winlator;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cli.CliService;
import com.winlator.cli.ContainerRuntime;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.StorageInfoDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.PreloaderDialog;
import com.winlator.xenvironment.RootFS;

import java.util.ArrayList;
import java.util.List;

public class ContainersFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private ContainerManager manager;
    private PreloaderDialog preloaderDialog;
    private final ContainerRuntime.Listener runtimeListener = new ContainerRuntime.Listener() {
        @Override
        public void onLog(String line) {}

        @Override
        public void onStatusChanged(ContainerRuntime.Status status, int exitCode) {
            if (getActivity() != null) getActivity().runOnUiThread(() -> {
                if (recyclerView != null && recyclerView.getAdapter() != null) recyclerView.getAdapter().notifyDataSetChanged();
            });
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        manager = new ContainerManager(getContext());
        loadContainersList();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.containers);
    }

    @Override
    public void onResume() {
        super.onResume();
        ContainerRuntime.getInstance().addListener(runtimeListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        ContainerRuntime.getInstance().removeListener(runtimeListener);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout frameLayout = (FrameLayout)inflater.inflate(R.layout.containers_fragment, container, false);
        recyclerView = frameLayout.findViewById(R.id.RecyclerView);
        Context context = recyclerView.getContext();
        emptyTextView = frameLayout.findViewById(R.id.TVEmptyText);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        DividerItemDecoration itemDecoration = new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL);
        itemDecoration.setDrawable(ContextCompat.getDrawable(context, R.drawable.list_item_divider));
        recyclerView.addItemDecoration(itemDecoration);
        return frameLayout;
    }

    private void loadContainersList() {
        ArrayList<Container> containers = manager.getContainers();
        recyclerView.setAdapter(new ContainersAdapter(containers));
        if (containers.isEmpty()) emptyTextView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.containers_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.menu_item_add) {
            if (!RootFS.find(getContext()).isValid()) return false;
            FragmentManager fragmentManager = getParentFragmentManager();
            fragmentManager.beginTransaction()
                .addToBackStack(null)
                .replace(R.id.FLFragmentContainer, new ContainerDetailFragment())
                .commit();
            return true;
        }
        else return super.onOptionsItemSelected(menuItem);
    }

    private class ContainersAdapter extends RecyclerView.Adapter<ContainersAdapter.ViewHolder> {
        private final List<Container> data;

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView runButton;
            private final ImageView stopButton;
            private final ImageView menuButton;
            private final ImageView imageView;
            private final TextView title;
            private final TextView statusView;

            private ViewHolder(View view) {
                super(view);
                this.imageView = view.findViewById(R.id.ImageView);
                this.title = view.findViewById(R.id.TVTitle);
                this.statusView = view.findViewById(R.id.TVStatus);
                this.runButton = view.findViewById(R.id.BTRun);
                this.stopButton = view.findViewById(R.id.BTStop);
                this.menuButton = view.findViewById(R.id.BTMenu);
            }
        }

        public ContainersAdapter(List<Container> data) {
            this.data = data;
        }

        @Override
        public final ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.container_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final Container item = data.get(position);
            holder.imageView.setImageResource(R.drawable.icon_container);
            holder.title.setText(item.getName());

            ContainerRuntime runtime = ContainerRuntime.getInstance();
            ContainerRuntime.Status status = runtime.isRunningContainer(item.id) ? ContainerRuntime.Status.RUNNING : (runtime.getContainer() != null && runtime.getContainer().id == item.id ? runtime.getStatus() : ContainerRuntime.Status.STOPPED);
            updateStatusView(holder, status);

            holder.runButton.setOnClickListener((view) -> runContainer(item));
            holder.stopButton.setOnClickListener((view) -> stopContainer(item));
            holder.menuButton.setOnClickListener((view) -> showListItemMenu(view, item));
        }

        private void updateStatusView(ViewHolder holder, ContainerRuntime.Status status) {
            Context context = holder.statusView.getContext();
            int textResId;
            int color;
            switch (status) {
                case RUNNING:
                    textResId = R.string.status_running;
                    color = 0xff4caf50;
                    break;
                case CRASHED:
                    textResId = R.string.status_crashed;
                    color = 0xfff44336;
                    break;
                default:
                    textResId = R.string.status_stopped;
                    color = AppUtils.getThemeColor(context, R.attr.colorSecondaryText);
                    break;
            }
            holder.statusView.setText(textResId);
            holder.statusView.setTextColor(color);

            boolean running = status == ContainerRuntime.Status.RUNNING;
            holder.stopButton.setVisibility(running ? View.VISIBLE : View.GONE);
            holder.runButton.setImageResource(running ? R.drawable.icon_open : R.drawable.icon_run);
        }

        @Override
        public final int getItemCount() {
            return data.size();
        }

        private void showListItemMenu(View anchorView, Container container) {
            MainActivity activity = (MainActivity)getActivity();
            PopupMenu listItemMenu = new PopupMenu(activity, anchorView);
            listItemMenu.inflate(R.menu.container_popup_menu);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listItemMenu.setForceShowIcon(true);

            listItemMenu.setOnMenuItemClickListener((menuItem) -> {
                switch (menuItem.getItemId()) {
                    case R.id.menu_item_file_manager:
                        activity.showFragment(new ContainerFileManagerFragment(container.id));
                        break;
                    case R.id.menu_item_edit:
                        activity.showFragment(new ContainerDetailFragment(container.id));
                        break;
                    case R.id.menu_item_duplicate:
                        ContentDialog.confirm(getContext(), R.string.do_you_want_to_duplicate_this_container, () -> {
                            preloaderDialog.show(R.string.duplicating_container);
                            manager.duplicateContainerAsync(container, () -> {
                                preloaderDialog.close();
                                loadContainersList();
                            });
                        });
                        break;
                    case R.id.menu_item_remove:
                        ContentDialog.confirm(getContext(), R.string.do_you_want_to_remove_this_container, () -> {
                            preloaderDialog.show(R.string.removing_container);
                            manager.removeContainerAsync(container, () -> {
                                preloaderDialog.close();
                                loadContainersList();
                            });
                        });
                        break;
                    case R.id.menu_item_info:
                        (new StorageInfoDialog(activity, container)).show();
                        break;
                }
                return true;
            });
            listItemMenu.show();
        }

        private void runContainer(Container container) {
            MainActivity activity = (MainActivity)getActivity();
            ContainerRuntime runtime = ContainerRuntime.getInstance();

            if (runtime.isRunningContainer(container.id)) {
                activity.showFragment(new ContainerConsoleFragment(container.id));
                return;
            }

            if (runtime.isRunning()) {
                AppUtils.showToast(activity, R.string.container_already_running);
                return;
            }

            ensureBatteryOptimizationExempt(activity);

            String execPath = container.getExecPath();
            Intent intent = new Intent(activity, CliService.class);
            intent.putExtra("container_id", container.id);
            intent.putExtra("exec_path", execPath.isEmpty() ? "cmd" : execPath);
            ContextCompat.startForegroundService(activity, intent);
            activity.showFragment(new ContainerConsoleFragment(container.id));
        }

        private void ensureBatteryOptimizationExempt(MainActivity activity) {
            PowerManager pm = (PowerManager)activity.getSystemService(Context.POWER_SERVICE);
            if (pm == null || pm.isIgnoringBatteryOptimizations(activity.getPackageName())) return;

            ContentDialog.confirm(activity, R.string.cli_battery_optimization_message, () -> {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:"+activity.getPackageName()));
                activity.startActivity(intent);
            });
        }

        private void stopContainer(Container container) {
            ContainerRuntime.getInstance().stop();
        }
    }
}
