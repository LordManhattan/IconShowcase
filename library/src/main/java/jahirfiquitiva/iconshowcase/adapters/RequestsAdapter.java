package jahirfiquitiva.iconshowcase.adapters;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Priority;
import com.pitchedapps.butler.iconrequest.App;
import com.pitchedapps.butler.iconrequest.IconRequest;

import java.util.ArrayList;

import jahirfiquitiva.iconshowcase.R;
import jahirfiquitiva.iconshowcase.config.Config;
import jahirfiquitiva.iconshowcase.dialogs.ISDialogs;
import jahirfiquitiva.iconshowcase.utilities.Preferences;
import jahirfiquitiva.iconshowcase.utilities.utils.RequestUtils;

public class RequestsAdapter extends RecyclerView.Adapter<RequestsAdapter.RequestsHolder> {

    private RequestsHolder holder;

    @Nullable
    public ArrayList<App> getApps() {
        if (IconRequest.get() != null)
            return IconRequest.get().getApps();
        return null;
    }

    @Nullable
    public ArrayList<App> getSelectedApps() {
        if (IconRequest.get() != null)
            return IconRequest.get().getSelectedApps();
        return null;
    }

    @Override
    public RequestsHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Preferences mPrefs = new Preferences(parent.getContext());
        View view = LayoutInflater.from(parent.getContext())
                .inflate((Config.get().devOptions() ?
                        mPrefs.getDevListsCards() : Config.get().bool(R.bool.request_cards))
                        ? R.layout.card_app_to_request :
                        R.layout.item_app_to_request, parent, false);
        return new RequestsHolder(view, viewType);
    }

    @Override
    public int getItemCount() {
        return getApps() != null ? getApps().size() : 0;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @Override
    public void onBindViewHolder(RequestsHolder holder, int position) {
        //noinspection ConstantConditions
        this.holder = holder;

        final App app = getApps().get(position);
        app.loadIcon(holder.imgIcon, Priority.IMMEDIATE);

        holder.txtName.setText(app.getName());
        final IconRequest ir = IconRequest.get();
        holder.itemView.setActivated(ir != null && ir.isAppSelected(app));

        holder.checkBox.setChecked(ir != null && ir.isAppSelected(app));
    }

    public void selectOrDeselectAll(Context context, boolean select, Preferences mPrefs) {
        boolean showDialog = false;
        final IconRequest ir = IconRequest.get();
        int limit = RequestUtils.canRequestXApps(context, mPrefs);
        if (ir != null && ir.getApps() != null) {
            if (limit >= -1) {
                for (App app : ir.getApps()) {
                    if (select) {
                        if (limit < 0) {
                            ir.selectApp(app);
                        } else {
                            if (limit > 0) {
                                if (ir.getSelectedApps().size() < limit) {
                                    ir.selectApp(app);
                                } else {
                                    showDialog = true;
                                    break;
                                }
                            }
                        }
                    } else {
                        ir.unselectApp(app);
                    }
                }
                //TODO: Either keep this or find a way to set checked/unchecked checkboxes in holder
                notifyDataSetChanged();
            }
            if (showDialog) ISDialogs.showRequestLimitDialog(context, limit);
        }
    }

    public class RequestsHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        final ImageView imgIcon;
        final TextView txtName;
        final AppCompatCheckBox checkBox;
        final int position;

        public RequestsHolder(View v, int i) {
            super(v);
            imgIcon = (ImageView) v.findViewById(R.id.imgIcon);
            txtName = (TextView) v.findViewById(R.id.txtName);
            checkBox = (AppCompatCheckBox) v.findViewById(R.id.chkSelected);
            position = i;
            v.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            final IconRequest ir = IconRequest.get();
            if (ir != null && ir.getApps() != null) {
                Preferences mPrefs = new Preferences(view.getContext());
                int limit = mPrefs.getRequestsLeft();
                final App app = ir.getApps().get(position);
                if (limit < 0) {
                    ir.toggleAppSelected(app);
                    checkBox.setChecked(ir.isAppSelected(app));
                } else {
                    if (Config.get().integer(R.integer.time_limit_in_minutes) <= 0) {
                        ir.toggleAppSelected(app);
                        checkBox.setChecked(ir.isAppSelected(app));
                    } else if (ir.getSelectedApps().size() < limit) {
                        ir.toggleAppSelected(app);
                        checkBox.setChecked(ir.isAppSelected(app));
                    } else {
                        if (ir.isAppSelected(ir.getApps().get(position))) {
                            ir.toggleAppSelected(app);
                            checkBox.setChecked(ir.isAppSelected(app));
                        } else {
                            if (Config.get().integer(R.integer.max_apps_to_request) > -1) {
                                if (RequestUtils.canRequestXApps(view.getContext(), mPrefs) > -2) {
                                    ISDialogs.showRequestLimitDialog(view.getContext(), limit);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}