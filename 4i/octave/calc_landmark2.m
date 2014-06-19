function q = calc_landmark(im,b,M_num)
  [h,w,ch] = size(im);
  tmp = (uint16)(bitshift(im2uint8(im),b-8));
  index = bitshift(tmp(:,:,1),2*b)+bitshift(tmp(:,:,2),b)+tmp(:,:,3);
  rep_index = repmat(index,[1,1,3]);
  
  %select the popular index
  all_index = sort(index(:));
  all_index_unique = unique(all_index);
  all_index_num = cat(2,all_index_unique,histc(all_index,unique(all_index)));
  while(size(all_index_num)(1) < M_num)
    all_index_num = cat(1,all_index_num,all_index_num);
  end
  select_index = sortrows(all_index_num,2)(end-M_num+1:end,1);
  %select the popular avg color
  select_color = zeros(M_num,3);
  for k=1:M_num
    curr_index = rep_index == select_index(k);
    num = sum(curr_index(:)) / 3;
    select_color(k,:) = sum(sum(im.*curr_index,1),2)(:)' ./ num;
  end
  
  %calc result
  for k=1:M_num
    color = select_color(k,:);
    color_diff = cat(3,im(:,:,1)-color(1),im(:,:,2)-color(2),im(:,:,3)-color(3));
    value = sum((color_diff).^2,3);
    [the_min,place] = min(value'(:));
    place = uint32(place)-1;
    q(k,:) = [(place-mod(place,w))/w+1,mod(place,w)+1];
  end
end

%for testing
function reconstruct_show(File)
  im = im2double(imread(File));
  [n,m,ch] = size(im);
  if(ch==1)
    im = repmat(im,[1,1,3]);
  end
  figure;
  imshow(im);
  M_num = 30;
  b = 3;
  im2 = reconstruct(im,b,M_num);
  figure;
  imshow(im2);
end

function out = reconstruct(im,b,M_num)
  [h,w,ch] = size(im);
  out = zeros(h,w,ch);
  tmp = (uint16)(bitshift(im2uint8(im),b-8));
  index = bitshift(tmp(:,:,1),2*b)+bitshift(tmp(:,:,2),b)+tmp(:,:,3);
  rep_index = repmat(index,[1,1,3]);
  
  %select the popular index
  all_index = sort(index(:));
  all_index_unique = unique(all_index);
  all_index_num = cat(2,all_index_unique,histc(all_index,unique(all_index)));
  if(M_num > size(all_index_unique))
    M_num = size(all_index_unique);
  end
  select_index = sortrows(all_index_num,2)(end-M_num+1:end,1);
  %select the popular avg color
  select_color = zeros(M_num,3);
  for k=1:M_num
    curr_index = rep_index == select_index(k);
    num = sum(curr_index(:)) / 3;
    select_color(k,:) = sum(sum(im.*curr_index,1),2)(:)' ./ num;
  end
  
  %reconstruct
  for k=1:M_num
    bool = (rep_index == select_index(k));
    out += cat(3,bool(:,:,1).*select_color(k,1),bool(:,:,2).*select_color(k,2),bool(:,:,3).*select_color(k,3));
  end
end