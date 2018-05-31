package net.osmand.plus.dialogs;


import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.CompoundButtonCompat;
import android.support.v4.widget.NestedScrollView;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import net.osmand.AndroidUtils;
import net.osmand.Collator;
import net.osmand.OsmAndCollator;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.MenuBottomSheetDialogFragment;
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem;
import net.osmand.plus.base.bottomsheetmenu.simpleitems.TitleItem;
import net.osmand.plus.openseamapsplugin.NauticalMapsPlugin;
import net.osmand.plus.quickaction.QuickActionFactory;
import net.osmand.plus.quickaction.actions.MapStyleAction;
import net.osmand.plus.quickaction.actions.MapSourceAction;
import net.osmand.plus.quickaction.actions.MapOverlayAction;
import net.osmand.plus.quickaction.actions.MapUnderlayAction;
import net.osmand.plus.render.RendererRegistry;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.render.RenderingRulesStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

public class SelectMapStyleQuickActionBottomSheet extends MenuBottomSheetDialogFragment {

	public static final String TAG = SelectMapStyleQuickActionBottomSheet.class.getSimpleName();

	private static final String SELECTED_STYLE_KEY = "selected_style";

	private LinearLayout stylesContainer;
	private View.OnClickListener onStyleClickListener;
	private ColorStateList rbColorList;

	private ArrayList<String> stylesMap;
	private String selectedStyle;
	private int type;

	@Override
	public void createMenuItems(Bundle savedInstanceState) {
		final Context context = getContext();
		if (context == null) {
			return;
		}
		if (getArguments() == null) {
			return;
		}
		Bundle args = getArguments();
		stylesMap = args.getStringArrayList("test");
		type = args.getInt("type");
		if (stylesMap == null || stylesMap.isEmpty() || type == 0) {
			return;
		}
		if (savedInstanceState == null) {
			RenderingRulesStorage current = getMyApplication().getRendererRegistry().getCurrentSelectedRenderer();
			if (current != null) {
				selectedStyle = current.getName();
			}
		} else {
			selectedStyle = savedInstanceState.getString(SELECTED_STYLE_KEY);
		}
		if (selectedStyle == null) {
			selectedStyle = RendererRegistry.DEFAULT_RENDER;
		}
		rbColorList = AndroidUtils.createCheckedColorStateList(context, R.color.icon_color, getActiveColorId());

		items.add(new TitleItem(getString(R.string.map_underlay)));

		NestedScrollView nestedScrollView = new NestedScrollView(context);
		stylesContainer = new LinearLayout(context);
		stylesContainer.setLayoutParams((new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)));
		stylesContainer.setOrientation(LinearLayout.VERTICAL);
		stylesContainer.setPadding(0, getResources().getDimensionPixelSize(R.dimen.bottom_sheet_content_padding_small), 0, 0);
		for (int i = 0; i < stylesMap.size(); i++) {
			LayoutInflater.from(new ContextThemeWrapper(context, themeRes))
					.inflate(R.layout.bottom_sheet_item_with_radio_btn, stylesContainer, true);
		}
		nestedScrollView.addView(stylesContainer);
		items.add(new BaseBottomSheetItem.Builder().setCustomView(nestedScrollView).create());

		populateStylesList();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(SELECTED_STYLE_KEY, selectedStyle);
	}

	@Override
	protected int getRightBottomButtonTextId() {
		return R.string.shared_string_close;
	}

	@Override
	protected void onRightBottomButtonClick() {
		dismiss();
	}

	@Override
	protected int getDismissButtonTextId() {
		return R.string.quick_action_edit_actions;
	}

	@Override
	protected void onDismissButtonClickAction() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity == null) {
			return;
		}
		OsmandApplication app = mapActivity.getMyApplication();
		RenderingRulesStorage loaded = app.getRendererRegistry().getRenderer(selectedStyle);
		if (loaded != null) {
			OsmandMapTileView view = mapActivity.getMapView();
			view.getSettings().RENDERER.set(selectedStyle);
			app.getRendererRegistry().setCurrentSelectedRender(loaded);
			ConfigureMapMenu.refreshMapComplete(mapActivity);
			mapActivity.getDashboard().refreshContent(true);
		} else {
			Toast.makeText(mapActivity, R.string.renderer_load_exception, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	protected boolean useScrollableItemsContainer() {
		return false;
	}

	@Nullable
	private MapActivity getMapActivity() {
		Activity activity = getActivity();
		if (activity != null && activity instanceof MapActivity) {
			return (MapActivity) activity;
		}
		return null;
	}

	@NonNull
	private TreeMap<String, String> generateStylesMap(Context context) {
		final Collator collator = OsmAndCollator.primaryCollator();
		TreeMap<String, String> res = new TreeMap<>(new Comparator<String>() {
			@Override
			public int compare(String string1, String string2) {
				if (string1.equals(RendererRegistry.DEFAULT_RENDER)) {
					return -1;
				}
				if (string2.equals(RendererRegistry.DEFAULT_RENDER)) {
					return 1;
				}
				return collator.compare(string1, string2);
			}
		});

		List<String> names = new ArrayList<>(getMyApplication().getRendererRegistry().getRendererNames());
		if (OsmandPlugin.getEnabledPlugin(NauticalMapsPlugin.class) == null) {
			names.remove(RendererRegistry.NAUTICAL_RENDER);
		}
		for (String name : names) {
			String translation = RendererRegistry.getTranslatedRendererName(context, name);
			if (translation == null) {
				translation = name.replace('_', ' ').replace('-', ' ');
			}
			res.put(translation, name);
		}

		return res;
	}

	@SuppressWarnings("RedundantCast")
	private void populateStylesList() {
		Context context = getContext();
		if (context == null) {
			return;
		}
		int counter = 0;
		for (String entry : stylesMap) {
			boolean selected = entry.equals(selectedStyle);

			View view = stylesContainer.getChildAt(counter);
			view.setTag(entry);
			view.setOnClickListener(getOnStyleClickListener());

			TextView titleTv = (TextView) view.findViewById(R.id.title);
			titleTv.setText(entry);
			titleTv.setTextColor(getStyleTitleColor(selected));

			RadioButton rb = (RadioButton) view.findViewById(R.id.compound_button);
			rb.setChecked(selected);
			CompoundButtonCompat.setButtonTintList(rb, rbColorList);
			ImageView imageView = (ImageView) view.findViewById(R.id.icon);
			imageView.setImageDrawable(((OsmandApplication) context.getApplicationContext())
					.getIconsCache().getThemedIcon(QuickActionFactory.getActionIcon(type)));
			counter++;
		}
	}

	@ColorInt
	private int getStyleTitleColor(boolean selected) {
		int colorId = selected
				? getActiveColorId()
				: nightMode ? R.color.primary_text_dark : R.color.primary_text_light;
		return getResolvedColor(colorId);
	}

	private View.OnClickListener getOnStyleClickListener() {
		if (onStyleClickListener == null) {
			onStyleClickListener = new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Context context = getContext();
					if (context == null) {
						return;
					}
					selectedStyle = (String) v.getTag();
					populateStylesList();
				}
			};
		}
		return onStyleClickListener;
	}
}
