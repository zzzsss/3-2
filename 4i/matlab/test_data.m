%testing

function ret = test_data(show,write)
    files = get_data();
    sizef = size(files);
    for i = 1:sizef(1)
        f = files{i};
        fprintf(f);
        fprintf('\n');
        result = matlab_rgb_lab(f,show);
        if(write)
            imwrite(result,cat(1,[f,'_gray.png']));
        end
    end
end

function data = get_data()
data = {'test/155_5572_jpg.ppm';'test/25_color.ppm';'test/34445.ppm';
    'test/arctichare.ppm';'test/balls0_color.ppm';'test/butterfly.ppm';
    'test/C8TZ7768.ppm';'test/ColorsPastel.ppm';'test/ColorWheelEqLum200.ppm';
    'test/DSCN9952.ppm';'test/fruits.ppm';'test/girl.ppm';'test/IM2-color.ppm';
    'test/impatient_color.ppm';'test/kodim03.ppm';'test/monarch.ppm';
    'test/portrait_4v.ppm';'test/ramp.ppm';'test/serrano.ppm';
    'test/Ski_TC8-03_sRGB.ppm';'test/Sunrise312.ppm';
    'test/text.ppm';'test/tree_color.ppm';'test/tulips.ppm';'test/watch.ppm'};
end
