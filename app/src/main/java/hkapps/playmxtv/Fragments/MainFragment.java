/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package hkapps.playmxtv.Fragments;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Response;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;

import org.xmlpull.v1.XmlPullParserException;

import hkapps.playmxtv.Activities.DetailsActivity;
import hkapps.playmxtv.Activities.BrowseErrorActivity;
import hkapps.playmxtv.Adapters.CardPresenter;
import hkapps.playmxtv.Model.Enlace;
import hkapps.playmxtv.Model.Ficha;
import hkapps.playmxtv.Model.Pelicula;
import hkapps.playmxtv.Model.Usuario;
import hkapps.playmxtv.R;
import hkapps.playmxtv.Scrapper.ScrapperListener;
import hkapps.playmxtv.Scrapper.StreamCloudRequest;
import hkapps.playmxtv.Services.PlayMaxAPI;
import hkapps.playmxtv.Services.Requester;
import hkapps.playmxtv.Static.MyUtils;

public class MainFragment extends BrowseFragment {
    private static final String TAG = "MainFragment";

    private static final int BACKGROUND_UPDATE_DELAY = 300;
    private static final int GRID_ITEM_WIDTH = 200;
    private static final int GRID_ITEM_HEIGHT = 200;

    private final Handler mHandler = new Handler();
    private ArrayObjectAdapter mRowsAdapter;
    private Drawable mDefaultBackground;
    private DisplayMetrics mMetrics;
    private Timer mBackgroundTimer;
    private URI mBackgroundURI;
    private BackgroundManager mBackgroundManager;

    private Usuario user;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onActivityCreated(savedInstanceState);

        prepareBackgroundManager();

        setupUIElements();

        //Recuperamos el usuario
        recoverUser();

        //Creamos el adaptador
        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

        //Pedimos las series
        Requester.request(getActivity(), PlayMaxAPI.getInstance().requestSumary(user), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    List<Ficha> fichas = Ficha.listFromXML(response);
                    Log.d("REQ",fichas.toString());

                    loadMyRows(fichas);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        Requester.request(getActivity(), PlayMaxAPI.getInstance().requestCatalogue(user), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    List<Ficha> fichas = Ficha.listFromXML(response);
                    Log.d("REQ",fichas.toString());

                    loadRecomendedRows(fichas);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        setupEventListeners();
    }

    private void recoverUser(){
        Intent me = this.getActivity().getIntent();
        user = (Usuario) me.getSerializableExtra("user");

        Log.d("REQ", user.toString());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != mBackgroundTimer) {
            Log.d(TAG, "onDestroy: " + mBackgroundTimer.toString());
            mBackgroundTimer.cancel();
        }
    }

    private void loadMyRows(List<Ficha> fichas) {

        CardPresenter cardPresenter = new CardPresenter();

        ArrayObjectAdapter proximos = new ArrayObjectAdapter(cardPresenter);
        ArrayObjectAdapter series = new ArrayObjectAdapter(cardPresenter);
        ArrayObjectAdapter peliculas = new ArrayObjectAdapter(cardPresenter);

        HeaderItem hproximos = new HeaderItem("Proximos Capitulos");
        HeaderItem hseries = new HeaderItem("Tus Series");
        HeaderItem hpeliculas = new HeaderItem("Tus Peliculas");

        for(Ficha fr : fichas){
            if(fr.getLastEpisode() != null)
                proximos.add(fr);

            if(fr.isSerie())
                series.add(fr);
            else peliculas.add(fr);
        }
        if(proximos.size() > 0) mRowsAdapter.add(new ListRow(0,hproximos, proximos));
        mRowsAdapter.add(new ListRow(1,hseries, series));
        mRowsAdapter.add(new ListRow(2,hpeliculas, peliculas));

        /*
        HeaderItem gridHeader = new HeaderItem(i, "PREFERENCES");

        GridItemPresenter mGridPresenter = new GridItemPresenter();
        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(mGridPresenter);
        gridRowAdapter.add(getResources().getString(R.string.grid_view));
        gridRowAdapter.add(getString(R.string.error_fragment));
        gridRowAdapter.add(getResources().getString(R.string.personal_settings));
        mRowsAdapter.add(new ListRow(gridHeader, gridRowAdapter));

        */
        setAdapter(mRowsAdapter);

    }
    private void loadRecomendedRows(List<Ficha> fichas) {

        CardPresenter cardPresenter = new CardPresenter();

        ArrayObjectAdapter series = new ArrayObjectAdapter(cardPresenter);
        ArrayObjectAdapter peliculas = new ArrayObjectAdapter(cardPresenter);

        HeaderItem hseries = new HeaderItem(3,"Series Recomendadas");
        HeaderItem hpeliculas = new HeaderItem(4,"Peliculas Recomendadas");

        for(Ficha fr : fichas){
            if(fr.isSerie())
                series.add(fr);
            else peliculas.add(fr);
        }
        mRowsAdapter.add(new ListRow(hseries, series));
        mRowsAdapter.add(new ListRow(hpeliculas, peliculas));

        /*
        HeaderItem gridHeader = new HeaderItem(i, "PREFERENCES");

        GridItemPresenter mGridPresenter = new GridItemPresenter();
        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(mGridPresenter);
        gridRowAdapter.add(getResources().getString(R.string.grid_view));
        gridRowAdapter.add(getString(R.string.error_fragment));
        gridRowAdapter.add(getResources().getString(R.string.personal_settings));
        mRowsAdapter.add(new ListRow(gridHeader, gridRowAdapter));

        */
        setAdapter(mRowsAdapter);

    }

    private void prepareBackgroundManager() {

        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.attach(getActivity().getWindow());
        mDefaultBackground = getResources().getDrawable(R.drawable.default_background);
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void setupUIElements() {
        // setBadgeDrawable(getActivity().getResources().getDrawable(
        // R.drawable.videos_by_google_banner));
        setTitle("PlayMax.TV"); // Badge, when set, takes precedent
        // over title
        //setHeadersState(HEADERS_ENABLED);
        setHeadersState(HEADERS_DISABLED);
        setHeadersTransitionOnBackEnabled(true);

        // set fastLane (or headers) background color
        setBrandColor(getResources().getColor(R.color.fastlane_background));
        // set search icon color
        setSearchAffordanceColor(getResources().getColor(R.color.search_opaque));
    }

    private void setupEventListeners() {
        setOnSearchClickedListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Toast.makeText(getActivity(), "Implement your own in-app search", Toast.LENGTH_LONG)
                        .show();
            }
        });

        setOnItemViewClickedListener(new ItemViewClickedListener());
        //setOnItemViewSelectedListener(new ItemViewSelectedListener());
    }

    protected void updateBackground(String uri) {
        int width = mMetrics.widthPixels;
        int height = mMetrics.heightPixels;
        Glide.with(getActivity())
                .load(uri)
                .centerCrop()
                .error(mDefaultBackground)
                .into(new SimpleTarget<GlideDrawable>(width, height) {
                    @Override
                    public void onResourceReady(GlideDrawable resource,
                                                GlideAnimation<? super GlideDrawable>
                                                        glideAnimation) {
                        mBackgroundManager.setDrawable(resource);
                    }
                });
        mBackgroundTimer.cancel();
    }

    private void startBackgroundTimer() {
        if (null != mBackgroundTimer) {
            mBackgroundTimer.cancel();
        }
        mBackgroundTimer = new Timer();
        mBackgroundTimer.schedule(new UpdateBackgroundTask(), BACKGROUND_UPDATE_DELAY);
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(final Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Ficha) {
                final Ficha fr = (Ficha) item;

                //Interfaz para peliculas
                Requester.request(MainFragment.this.getActivity(), PlayMaxAPI.getInstance().requestFicha(user, fr), new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            fr.completeFromXML(response);

                            if(fr.getIdCapitulo()!= null){
                                //Si tenemos capitulo: Lanzamos el detail para capitulo
                            }else if(fr.isSerie()){
                                //Si es Serie
                            }else {
                                //Es pelicula: lanzamos el selector de pelicula
                                Intent intent = new Intent(getActivity(), DetailsActivity.class);
                                intent.putExtra(DetailsActivity.MOVIE, fr);
                                intent.putExtra(DetailsActivity.USER, user);

                                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                                        getActivity(),
                                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                                        DetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                                getActivity().startActivity(intent, bundle);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });


                if(fr.getIdCapitulo()!= null){
                    //Recuperar el primer enlace streamcloud de los que me den y lanzar MX Player.
                    Requester.request(MainFragment.this.getActivity(),
                            PlayMaxAPI.getInstance().requestEnlaces(user, fr, fr.getIdCapitulo()),
                            new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            //Log.d("REQ", response);
                            try {
                                List<Enlace> enlaces = Enlace.listFromXML(response);
                                if(enlaces.size()>0) {
                                    Log.d("REQ", enlaces.toString());
                                    StreamCloudRequest.getDirectUrl(MainFragment.this.getActivity(), enlaces.get(0).toString(), new ScrapperListener() {
                                        @Override
                                        public void onDirectUrlObtained(String direct_url) {
                                            MyUtils.launchMXP(getActivity(), direct_url);
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }else if(fr.isSerie()){
                    //Interfaz para series
                }else{
                    //Interfaz para peliculas
                    Requester.request(MainFragment.this.getActivity(), PlayMaxAPI.getInstance().requestFicha(user, fr), new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            try {
                                fr.completeFromXML(response);

                                Intent intent = new Intent(getActivity(), DetailsActivity.class);
                                intent.putExtra(DetailsActivity.MOVIE, fr);
                                intent.putExtra(DetailsActivity.USER, user);

                                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                                        getActivity(),
                                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                                        DetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                                getActivity().startActivity(intent, bundle);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
                Log.d(TAG, "Item: " + item.toString());


            } else if (item instanceof String) {
                if (((String) item).indexOf(getString(R.string.error_fragment)) >= 0) {
                    Intent intent = new Intent(getActivity(), BrowseErrorActivity.class);
                    startActivity(intent);
                } else {
                    Toast.makeText(getActivity(), ((String) item), Toast.LENGTH_SHORT)
                            .show();
                }
            }
        }
    }

    private class UpdateBackgroundTask extends TimerTask {

        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mBackgroundURI != null) {
                        updateBackground(mBackgroundURI.toString());
                    }
                }
            });

        }
    }

    private class GridItemPresenter extends Presenter {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            TextView view = new TextView(parent.getContext());
            view.setLayoutParams(new ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT));
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            view.setBackgroundColor(getResources().getColor(R.color.default_background));
            view.setTextColor(Color.WHITE);
            view.setGravity(Gravity.CENTER);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            ((TextView) viewHolder.view).setText((String) item);
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
        }
    }

}
