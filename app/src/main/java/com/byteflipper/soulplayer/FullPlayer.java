package com.byteflipper.soulplayer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.byteflipper.soulplayer.databinding.FullPlayerBinding;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.IOException;

public class FullPlayer extends DialogFragment {
    private FullPlayerBinding binding;
    private PlayerViewModel playerViewModel;
    private MusicPlayer musicPlayer;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MaterialToolbar toolbar = requireActivity().findViewById(R.id.toolbar);
        toolbar.setVisibility(View.GONE);

        setStyle(DialogFragment.STYLE_NORMAL, R.style.BaseSoulPlayerTheme);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FullPlayerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        playerViewModel = new ViewModelProvider(requireActivity()).get(PlayerViewModel.class);

        // Создаем экземпляр MusicPlayer
        musicPlayer = MusicPlayer.getInstance(requireContext());

        // Слушатель кнопки play/pause
        binding.sheetMidButton.setOnClickListener(v -> {
            if (musicPlayer.isPlaying()) {
                musicPlayer.pause();
            } else {
                if (musicPlayer.isCompleted()) {
                    musicPlayer.seekTo(0);
                }
                musicPlayer.resume();
            }
            updatePlayButtonIcon();
        });

        musicPlayer.setOnPlaybackChangeListener(new MusicPlayer.OnPlaybackChangeListener() {
            @Override
            public void onStarted() {
                // Начало воспроизведения
                startUpdatingProgress();
            }

            @Override
            public void onPaused() {
                // Пауза воспроизведения
                stopUpdatingProgress();
            }

            @Override
            public void onResumed() {
                // Продолжение воспроизведения после паузы
                startUpdatingProgress();
            }

            @Override
            public void onStopped() {
                // Остановка воспроизведения
                stopUpdatingProgress();
            }

            @Override
            public void onProgressChanged(int progress) {
                // Обновление положения индикатора прогресса
                binding.sliderVert.setValue(progress);
                updateCurrentTime(progress);
            }
        });

        // Слушатель изменений положения слайдера
        binding.sliderVert.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                musicPlayer.seekTo((int) value);
            }
        });

        // Обновление UI при изменении текущей песни
        playerViewModel.currentSong.observe(getViewLifecycleOwner(), song -> {
            if (song != null) {
                binding.fullSongName.setText(song.title);
                binding.fullSongArtist.setText(song.artistName);
                binding.fullAlbumName.setText(song.albumName);

                try {
                    Bitmap albumArt = getAlbumArt(song.data);
                    if (albumArt != null) {
                        binding.fullSheetCover.setImageBitmap(albumArt);
                    } else {
                        binding.fullSheetCover.setImageResource(R.mipmap.ic_launcher);
                    }
                } catch (IOException e) {
                    Log.e("FullPlayer", "Ошибка загрузки обложки", e);
                    binding.fullSheetCover.setImageResource(R.mipmap.ic_launcher);
                }

                // Воспроизведение при выборе песни
                if (musicPlayer.isPlaying() || musicPlayer.isPaused()) {
                    musicPlayer.stop();
                }
                musicPlayer.play(song.data);
                updatePlayButtonIcon();
            }
        });

        updatePlayButtonIcon();
    }

    private Bitmap getAlbumArt(String path) throws IOException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            byte[] art = retriever.getEmbeddedPicture();
            if (art != null) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }
        } catch (IllegalArgumentException e) {
            Log.e("FullPlayer", "Ошибка получения обложки", e);
        } finally {
            retriever.release();
        }
        return null;
    }

    private void updatePlayButtonIcon() {
        if (musicPlayer.isPlaying()) {
            binding.sheetMidButton.setIconResource(R.drawable.pause_24px);
        } else {
            binding.sheetMidButton.setIconResource(R.drawable.play_arrow_24px);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void updateCurrentTime(int currentPosition) {
        int minutes = currentPosition / 60000;
        int seconds = (currentPosition % 60000) / 1000;
        String currentTime = String.format("%02d:%02d", minutes, seconds);
        binding.position.setText(currentTime);
    }

    private void updateTotalTime(int duration) {
        int minutes = duration / 60000;
        int seconds = (duration % 60000) / 1000;
        String totalTime = String.format("%02d:%02d", minutes, seconds);
        binding.duration.setText(totalTime);
    }

    // Метод для запуска обновления прогресса воспроизведения
    private void startUpdatingProgress() {
        handler.post(updateProgressTask);
    }

    // Метод для остановки обновления прогресса воспроизведения
    private void stopUpdatingProgress() {
        handler.removeCallbacks(updateProgressTask);
    }

    // Runnable для обновления прогресса воспроизведения
    private final Runnable updateProgressTask = new Runnable() {
        @Override
        public void run() {
            if (musicPlayer != null && musicPlayer.isPlaying()) {
                int currentPosition = musicPlayer.getCurrentPosition();
                int totalDuration = musicPlayer.getDuration();
                if (totalDuration > 0) {
                    float progressPercentage = ((float) currentPosition / totalDuration);
                    binding.sliderVert.setValue(progressPercentage);
                }
                handler.postDelayed(this, 1000); // Повторяем через каждую секунду
            }
        }
    };
}